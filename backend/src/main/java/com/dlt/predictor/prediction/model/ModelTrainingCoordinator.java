package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 *
 * 协调三种预测模型的训练、评估和同批次原子激活。
 *
 */
@Service
public class ModelTrainingCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelTrainingCoordinator.class);
    private static final int MINIMUM_DRAW_COUNT = 100;
    private static final String LOGISTIC_ALGORITHM = "logistic-l2-v1";
    private static final String VECTOR_ALGORITHM = "vector-transition-v1";
    private static final String ENSEMBLE_ALGORITHM = "dynamic-ensemble-v1";

    private final DrawStore drawStore;
    private final PredictionModelStore modelStore;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final ReentrantLock trainingLock = new ReentrantLock(true);

    public ModelTrainingCoordinator(
            DrawStore drawStore,
            PredictionModelStore modelStore,
            ObjectMapper objectMapper,
            @Qualifier("modelTrainingExecutor") TaskExecutor taskExecutor) {
        this.drawStore = drawStore;
        this.modelStore = modelStore;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    public TrainingRunStatus requestRetrain() {
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
                .map(batch -> batch.getFirst().trainedThroughDrawId() == latestDrawId)
                .orElse(false);
        return current ? TrainingRunStatus.SUCCESS : requestRetrain();
    }

    public TrainingRunResult retrainNow() {
        if (!trainingLock.tryLock()) {
            return TrainingRunResult.status(TrainingRunStatus.TRAINING_IN_PROGRESS, "已有模型训练任务正在执行");
        }
        try {
            return trainLocked();
        } catch (RuntimeException exception) {
            LOGGER.error("DLT model training failed; active batch remains unchanged", exception);
            return TrainingRunResult.status(TrainingRunStatus.FAILED, "模型训练失败: " + exception.getMessage());
        } finally {
            trainingLock.unlock();
        }
    }

    private TrainingRunResult trainLocked() {
        List<Draw> draws = drawStore.allChronological();
        if (draws.size() < MINIMUM_DRAW_COUNT) {
            return TrainingRunResult.status(
                    TrainingRunStatus.INSUFFICIENT_DATA,
                    "至少需要 " + MINIMUM_DRAW_COUNT + " 期开奖记录，当前为 " + draws.size() + " 期");
        }
        List<RollingFold> folds = new RollingFoldBuilder().build(draws.size(), TrainingDatasetBuilder.FEATURE_WINDOW);
        String batchVersion = UUID.randomUUID().toString();
        String logisticVersion = "logistic-" + batchVersion;
        String vectorVersion = "vector-" + batchVersion;
        String ensembleVersion = "ensemble-" + batchVersion;

        TrainedLogisticModel logistic = new NextDrawModelTrainer().train(draws, folds);
        TrainedVectorTransitionModel vector = new VectorTransitionModelTrainer().train(draws, folds);
        TrainedEnsembleModel ensemble = new DynamicEnsembleTrainer().train(
                draws, logistic.validationPredictions(), vector.validationPredictions(),
                logisticVersion, vectorVersion);
        Draw waterline = draws.getLast();
        Instant trainedAt = Instant.now();
        List<ModelSnapshot> candidates = List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, logisticVersion, LOGISTIC_ALGORITHM,
                        batchVersion, serialize(logistic.artifact()), TrainingDatasetBuilder.FEATURE_WINDOW,
                        draws.size(), logistic.sampleCount(), waterline, trainedAt, logistic.metrics()),
                snapshot(PredictionModelType.VECTOR_TRANSITION, vectorVersion, VECTOR_ALGORITHM,
                        batchVersion, serialize(vector.artifact()), 0,
                        draws.size(), vector.sampleCount(), waterline, trainedAt, vector.metrics()),
                snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, ensembleVersion, ENSEMBLE_ALGORITHM,
                        batchVersion, serialize(ensemble.artifact()), 0,
                        draws.size(), ensemble.validationPredictions().periods().size(),
                        waterline, trainedAt, ensemble.metrics()));

        boolean retainActive = !ensemble.eligible() && modelStore.activeBatch().isPresent();
        List<ModelSnapshot> persisted = retainActive
                ? candidates.stream().map(modelStore::saveInactive).toList()
                : modelStore.activateBatch(candidates);
        TrainingRunStatus status = retainActive
                ? TrainingRunStatus.RETAINED_ACTIVE_MODEL : TrainingRunStatus.SUCCESS;
        String message = retainActive
                ? "新模型未达到启用条件，已保留当前生效批次"
                : "三种预测模型已完成训练并生效";
        return new TrainingRunResult(
                status,
                batchVersion,
                message,
                persisted.stream().map(ModelTrainingResult::from).toList());
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
