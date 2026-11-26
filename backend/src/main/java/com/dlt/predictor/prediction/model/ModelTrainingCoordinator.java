package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 *
 * 协调五种预测模型的训练、评估和同批次原子激活。
 *
 */
@Service
public class ModelTrainingCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelTrainingCoordinator.class);
    private static final int MINIMUM_DRAW_COUNT = 100;
    private static final String LOGISTIC_ALGORITHM = "logistic-multi-window-v3";
    private static final String VECTOR_ALGORITHM = "vector-transition-hit-window-v4";
    private static final String ENSEMBLE_ALGORITHM = "dynamic-ensemble-window-v4";
    private static final String DECAYED_FREQUENCY_ALGORITHM = "historical-position-gaussian-v2";
    private static final String FREQUENCY_SIMILARITY_ALGORITHM = "frequency-similarity-window-v3";

    private final DrawStore drawStore;
    private final PredictionModelStore modelStore;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final MultiWindowModelOptimizer optimizer;
    private final ReentrantLock trainingLock = new ReentrantLock(true);
    private final AtomicBoolean retrainPending = new AtomicBoolean();

    @Autowired
    public ModelTrainingCoordinator(
            DrawStore drawStore,
            PredictionModelStore modelStore,
            ObjectMapper objectMapper,
            @Qualifier("modelTrainingExecutor") TaskExecutor taskExecutor) {
        this(drawStore, modelStore, objectMapper, taskExecutor, new MultiWindowModelOptimizer());
    }

    ModelTrainingCoordinator(
            DrawStore drawStore,
            PredictionModelStore modelStore,
            ObjectMapper objectMapper,
            TaskExecutor taskExecutor,
            NextDrawModelTrainer logisticTrainer,
            VectorTransitionModelTrainer vectorTrainer,
            DynamicEnsembleTrainer ensembleTrainer,
            DecayedFrequencyModelTrainer decayedTrainer,
            FrequencySimilarityTrainer frequencySimilarityTrainer) {
        this(drawStore, modelStore, objectMapper, taskExecutor, new MultiWindowModelOptimizer(
                logisticTrainer, vectorTrainer, ensembleTrainer, decayedTrainer, frequencySimilarityTrainer));
    }

    ModelTrainingCoordinator(DrawStore drawStore, PredictionModelStore modelStore,
            ObjectMapper objectMapper, TaskExecutor taskExecutor, MultiWindowModelOptimizer optimizer) {
        this.drawStore = drawStore;
        this.modelStore = modelStore;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.optimizer = optimizer;
    }

    public TrainingRunStatus requestRetrain() {
        // 先登记再检查锁；与结束时的检查交错也不会丢失请求。
        retrainPending.set(true);
        return enqueuePending();
    }

    private TrainingRunStatus enqueuePending() {
        if (trainingLock.isLocked()) {
            return TrainingRunStatus.TRAINING_IN_PROGRESS;
        }
        try {
            taskExecutor.execute(this::retrainNow);
            return TrainingRunStatus.STARTED;
        } catch (RejectedExecutionException exception) {
            LOGGER.info("Model training request was coalesced because the queue is full");
            return TrainingRunStatus.TRAINING_IN_PROGRESS;
        }
    }

    public TrainingRunStatus requestIfStale() {
        List<Draw> draws = drawStore.allChronological();
        if (draws.size() < MINIMUM_DRAW_COUNT) {
            return TrainingRunStatus.INSUFFICIENT_DATA;
        }
        long latestDrawId = draws.getLast().id();
        boolean current = modelStore.activeBatch()
                .map(batch -> batch.getFirst().trainedThroughDrawId() == latestDrawId
                        && batch.getFirst().drawCount() == draws.size()
                        && hasCurrentAlgorithms(batch)
                        && hasCurrentFingerprint(batch, draws))
                .orElse(false);
        return current ? TrainingRunStatus.SUCCESS : requestRetrain();
    }

    private boolean hasCurrentFingerprint(List<ModelSnapshot> batch, List<Draw> draws) {
        ModelSnapshot logistic = batch.stream().filter(model -> model.type() == PredictionModelType.LOGISTIC_REGRESSION)
                .findFirst().orElseThrow();
        try {
            return TrainingHistoryFingerprint.calculate(draws).equals(objectMapper.readTree(logistic.configJson())
                    .path("trainingHistoryFingerprint").asText());
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private static boolean hasCurrentAlgorithms(List<ModelSnapshot> batch) {
        return hasAlgorithmVersion(batch, PredictionModelType.LOGISTIC_REGRESSION, LOGISTIC_ALGORITHM)
                && hasAlgorithmVersion(batch, PredictionModelType.VECTOR_TRANSITION, VECTOR_ALGORITHM)
                && hasAlgorithmVersion(batch, PredictionModelType.DYNAMIC_ENSEMBLE, ENSEMBLE_ALGORITHM)
                && hasAlgorithmVersion(batch, PredictionModelType.DECAYED_FREQUENCY, DECAYED_FREQUENCY_ALGORITHM)
                && hasAlgorithmVersion(batch, PredictionModelType.FREQUENCY_SIMILARITY, FREQUENCY_SIMILARITY_ALGORITHM);
    }

    private static boolean hasAlgorithmVersion(
            List<ModelSnapshot> batch, PredictionModelType type, String algorithmVersion) {
        return batch.stream().anyMatch(snapshot -> snapshot.type() == type
                && algorithmVersion.equals(snapshot.algorithmVersion()));
    }

    public TrainingRunResult retrainNow() {
        retrainPending.set(true);
        if (!trainingLock.tryLock()) {
            return TrainingRunResult.status(TrainingRunStatus.TRAINING_IN_PROGRESS, "已有模型训练任务正在执行");
        }
        retrainPending.set(false);
        try {
            return trainLocked();
        } catch (RuntimeException exception) {
            LOGGER.error("DLT model training failed; active batch remains unchanged", exception);
            return TrainingRunResult.status(TrainingRunStatus.FAILED, "模型训练失败: " + exception.getMessage());
        } finally {
            trainingLock.unlock();
            if (retrainPending.get()) {
                enqueuePending();
            }
        }
    }

    private TrainingRunResult trainLocked() {
        List<Draw> draws = drawStore.allChronological();
        if (draws.size() < MINIMUM_DRAW_COUNT) {
            return TrainingRunResult.status(
                    TrainingRunStatus.INSUFFICIENT_DATA,
                    "至少需要 " + MINIMUM_DRAW_COUNT + " 期开奖记录，当前为 " + draws.size() + " 期");
        }
        String fingerprint = TrainingHistoryFingerprint.calculate(draws);
        ModelOptimizationResult optimization = optimizer.optimize(draws);
        if (!fingerprint.equals(TrainingHistoryFingerprint.calculate(drawStore.allChronological()))) {
            retrainPending.set(true);
            return new TrainingRunResult(TrainingRunStatus.HISTORY_CHANGED, null,
                    "训练期间历史开奖发生变化，本轮未保存或启用模型，已排队重新训练",
                    List.of(), optimization.summary());
        }
        String batchVersion = UUID.randomUUID().toString();
        String logisticVersion = "logistic-" + batchVersion;
        String vectorVersion = "vector-" + batchVersion;
        String ensembleVersion = "ensemble-" + batchVersion;
        String decayedVersion = "decayed-frequency-" + batchVersion;
        String frequencySimilarityVersion = "frequency-similarity-" + batchVersion;

        TrainedLogisticModel logistic = optimization.logistic();
        TrainedVectorTransitionModel vector = optimization.vector();
        TrainedEnsembleModel ensemble = optimization.ensemble();
        TrainedDecayedFrequencyModel decayed = optimization.normal();
        TrainedFrequencySimilarityModel frequencySimilarity = optimization.hybrid();
        // 未通过时只保存开发段制品，不谎称它已拟合验收段及最新水位。
        int fittedCount = optimization.eligible() ? draws.size() : optimization.summary().developmentDrawCount();
        Draw waterline = draws.get(fittedCount - 1);
        EnsembleArtifact ensembleArtifact = new EnsembleArtifact(1, ensemble.artifact().frontLogisticWeight(),
                ensemble.artifact().backLogisticWeight(), logisticVersion, vectorVersion);
        FrequencySimilarityArtifact hybridArtifact = new FrequencySimilarityArtifact(1,
                frequencySimilarity.artifact().frontDecayedFrequencyWeight(),
                frequencySimilarity.artifact().backDecayedFrequencyWeight(), decayedVersion, vectorVersion);
        Instant trainedAt = Instant.now();
        List<ModelSnapshot> candidates = List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, logisticVersion, LOGISTIC_ALGORITHM,
                        batchVersion, serialize(logistic.artifact()), TrainingDatasetBuilder.FEATURE_WINDOW,
                        fittedCount, logistic.sampleCount(), waterline, trainedAt, logistic.metrics()),
                snapshot(PredictionModelType.VECTOR_TRANSITION, vectorVersion, VECTOR_ALGORITHM,
                        batchVersion, serialize(vector.artifact()), 0,
                        fittedCount, vector.sampleCount(), waterline, trainedAt, vector.metrics()),
                snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, ensembleVersion, ENSEMBLE_ALGORITHM,
                        batchVersion, serialize(ensembleArtifact), 0,
                        fittedCount, ensemble.validationPredictions().periods().size(),
                        waterline, trainedAt, ensemble.metrics()),
                snapshot(PredictionModelType.DECAYED_FREQUENCY, decayedVersion, DECAYED_FREQUENCY_ALGORITHM,
                        batchVersion, serialize(decayed.artifact()), 0,
                        fittedCount, decayed.sampleCount(), waterline, trainedAt, decayed.metrics()),
                snapshot(PredictionModelType.FREQUENCY_SIMILARITY,
                        frequencySimilarityVersion, FREQUENCY_SIMILARITY_ALGORITHM,
                        batchVersion, serialize(hybridArtifact), 0,
                        fittedCount, frequencySimilarity.sampleCount(), waterline, trainedAt,
                        frequencySimilarity.metrics()));

        boolean eligible = optimization.eligible();
        boolean hasActive = modelStore.activeBatch().isPresent();
        List<ModelSnapshot> persisted = eligible ? modelStore.activateBatch(candidates)
                : candidates.stream().map(modelStore::saveInactive).toList();
        TrainingRunStatus status = eligible ? TrainingRunStatus.SUCCESS
                : hasActive ? TrainingRunStatus.RETAINED_ACTIVE_MODEL : TrainingRunStatus.NO_ELIGIBLE_MODEL;
        String message = eligible ? "五种模型通过独立验收，已重新拟合并整批生效"
                : hasActive ? "自动优化未通过独立验收，已保留原生效批次；旧批次若未覆盖最新开奖仍不可预测"
                : "自动优化未通过独立验收，尚无通过验收的生效模型；已保存非生效候选";
        return new TrainingRunResult(
                status,
                batchVersion,
                message,
                persisted.stream().map(ModelTrainingResult::from).toList(), optimization.summary());
    }

    private ModelSnapshot snapshot(
            PredictionModelType type,
            String modelVersion,
            String algorithmVersion,
            String batchVersion,
            String configJson,
            int featureWindow,
            int drawCount,
            int sampleCount,
            Draw waterline,
            Instant trainedAt,
            ModelMetrics metrics) {
        return new ModelSnapshot(
                type, modelVersion, algorithmVersion, batchVersion, PredictionModelStatus.INACTIVE,
                configJson, featureWindow, drawCount, sampleCount, waterline.id(), waterline.issueNo(),
                waterline.drawDate(), trainedAt, metrics);
    }

    private String serialize(Object artifact) {
        try {
            return objectMapper.writeValueAsString(artifact);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型参数序列化失败", exception);
        }
    }
}
