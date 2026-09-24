package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.dto.EnsembleWeightsResponse;
import com.dlt.predictor.prediction.dto.ModelMetricsResponse;
import com.dlt.predictor.prediction.dto.ModelPredictionResponse;
import com.dlt.predictor.prediction.dto.PredictionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 *
 * 使用同一生效批次的四种模型预测下一期开奖。
 *
 */
@Service
public class NextDrawPredictionService {

    private static final String DISCLAIMER = "仅基于历史开奖数据进行统计建模，用于分析与娱乐，不构成中奖承诺。";

    private final DrawStore drawStore;
    private final PredictionModelStore modelStore;
    private final VectorTransitionPredictor vectorPredictor;
    private final ObjectMapper objectMapper;

    public NextDrawPredictionService(
            DrawStore drawStore,
            PredictionModelStore modelStore,
            VectorTransitionPredictor vectorPredictor,
            ObjectMapper objectMapper) {
        this.drawStore = drawStore;
        this.modelStore = modelStore;
        this.vectorPredictor = vectorPredictor;
        this.objectMapper = objectMapper;
    }

    public PredictionResponse predict() {
        List<ModelSnapshot> batch = modelStore.activeBatch()
                .orElseThrow(() -> new ModelUnavailableException("尚无完整可用的预测模型，请先执行模型训练"));
        Map<PredictionModelType, ModelSnapshot> models = new EnumMap<>(PredictionModelType.class);
        batch.forEach(snapshot -> models.put(snapshot.type(), snapshot));
        if (models.size() != PredictionModelType.values().length) {
            throw new ModelUnavailableException("生效模型批次不完整");
        }

        List<Draw> history = drawStore.allChronological();
        if (history.size() < TrainingDatasetBuilder.FEATURE_WINDOW) {
            throw new ModelUnavailableException("开奖记录不足，无法预测下一期");
        }
        Draw latest = history.getLast();
        ModelSnapshot logisticSnapshot = models.get(PredictionModelType.LOGISTIC_REGRESSION);
        ModelSnapshot vectorSnapshot = models.get(PredictionModelType.VECTOR_TRANSITION);
        ModelSnapshot ensembleSnapshot = models.get(PredictionModelType.DYNAMIC_ENSEMBLE);
        ModelSnapshot decayedSnapshot = models.get(PredictionModelType.DECAYED_FREQUENCY);
        LogisticModelArtifact logisticArtifact = decode(
                logisticSnapshot.configJson(), LogisticModelArtifact.class, "逻辑回归");
        VectorTransitionArtifact vectorArtifact = decode(
                vectorSnapshot.configJson(), VectorTransitionArtifact.class, "向量转移");
        EnsembleArtifact ensembleArtifact = decode(
                ensembleSnapshot.configJson(), EnsembleArtifact.class, "动态集成");
        DecayedFrequencyArtifact decayedArtifact = decode(
                decayedSnapshot.configJson(), DecayedFrequencyArtifact.class, "历史衰减概率");
        validateArtifacts(logisticArtifact, vectorArtifact, ensembleArtifact, logisticSnapshot, vectorSnapshot);
        validateDecayedFrequency(decayedArtifact);

        NextDrawModelTrainer logisticTrainer = new NextDrawModelTrainer();
        CandidateScores logisticFront = logisticTrainer.scoreLatest(
                history, NumberZone.FRONT, logisticArtifact.frontWeights());
        CandidateScores logisticBack = logisticTrainer.scoreLatest(
                history, NumberZone.BACK, logisticArtifact.backWeights());
        CandidateScores vectorFront = vectorPredictor.predictLatest(
                history, NumberZone.FRONT, vectorArtifact.neighborCount());
        CandidateScores vectorBack = vectorPredictor.predictLatest(
                history, NumberZone.BACK, vectorArtifact.neighborCount());
        DynamicEnsembleTrainer ensembleTrainer = new DynamicEnsembleTrainer();
        CandidateScores ensembleFront = ensembleTrainer.combine(
                logisticFront, vectorFront, ensembleArtifact.frontLogisticWeight());
        CandidateScores ensembleBack = ensembleTrainer.combine(
                logisticBack, vectorBack, ensembleArtifact.backLogisticWeight());
        CandidateScores decayedFront = new CandidateScores(decayedArtifact.frontProbabilities());
        CandidateScores decayedBack = new CandidateScores(decayedArtifact.backProbabilities());

        EnsembleWeightsResponse weights = new EnsembleWeightsResponse(
                ensembleArtifact.frontLogisticWeight(), ensembleArtifact.frontVectorWeight(),
                ensembleArtifact.backLogisticWeight(), ensembleArtifact.backVectorWeight(),
                ensembleArtifact.logisticModelVersion(), ensembleArtifact.vectorModelVersion());
        return new PredictionResponse(
                Instant.now(),
                latest.issueNo(),
                DISCLAIMER,
                List.of(
                        response(logisticSnapshot, "逻辑回归", logisticFront, logisticBack, null),
                        response(vectorSnapshot, "向量转移", vectorFront, vectorBack, null),
                        response(ensembleSnapshot, "动态集成", ensembleFront, ensembleBack, weights),
                        response(decayedSnapshot, "历史衰减概率", decayedFront, decayedBack, null)));
    }

    private static ModelPredictionResponse response(
            ModelSnapshot snapshot,
            String name,
            CandidateScores front,
            CandidateScores back,
            EnsembleWeightsResponse weights) {
        DltNumbers numbers = DltNumbers.of(
                PredictionMetricsCalculator.topNumbers(front.values(), NumberZone.FRONT.selectionCount()),
                PredictionMetricsCalculator.topNumbers(back.values(), NumberZone.BACK.selectionCount()));
        return new ModelPredictionResponse(
                snapshot.type(), name, numbers, front.values(), back.values(), snapshot.modelVersion(),
                snapshot.trainedAt(), snapshot.drawCount(), snapshot.trainedThroughIssueNo(),
                ModelMetricsResponse.from(snapshot.metrics()), weights,
                snapshot.metrics().frontBeatsBaseline(), snapshot.metrics().backBeatsBaseline());
    }

    private static void validateArtifacts(
            LogisticModelArtifact logistic,
            VectorTransitionArtifact vector,
            EnsembleArtifact ensemble,
            ModelSnapshot logisticSnapshot,
            ModelSnapshot vectorSnapshot) {
        if (logistic.artifactVersion() != 1 || vector.artifactVersion() != 1 || ensemble.artifactVersion() != 1) {
            throw new ModelUnavailableException("模型参数版本不受支持，请重新训练");
        }
        if (logistic.featureWindow() != TrainingDatasetBuilder.FEATURE_WINDOW
                || logistic.frontWeights().length != 7 || logistic.backWeights().length != 7) {
            throw new ModelUnavailableException("逻辑回归模型参数已损坏，请重新训练");
        }
        if (vector.neighborCount() < 1) {
            throw new ModelUnavailableException("向量转移模型参数已损坏，请重新训练");
        }
        if (!ensemble.logisticModelVersion().equals(logisticSnapshot.modelVersion())
                || !ensemble.vectorModelVersion().equals(vectorSnapshot.modelVersion())) {
            throw new ModelUnavailableException("动态集成模型引用的基础模型版本不一致");
        }
    }

    private static void validateDecayedFrequency(DecayedFrequencyArtifact artifact) {
        if (artifact.artifactVersion() != 1
                || artifact.halfLifeDraws() != DecayedFrequencyModelTrainer.HALF_LIFE_DRAWS
                || !isCompleteProbabilityMap(artifact.frontProbabilities(), NumberZone.FRONT.candidateCount())
                || !isCompleteProbabilityMap(artifact.backProbabilities(), NumberZone.BACK.candidateCount())) {
            throw new ModelUnavailableException("历史衰减概率模型参数已损坏，请重新训练");
        }
    }

    private static boolean isCompleteProbabilityMap(Map<Integer, Double> values, int count) {
        return values != null
                && values.size() == count
                && IntStream.rangeClosed(1, count).allMatch(values::containsKey)
                && values.values().stream().allMatch(value -> value != null
                        && Double.isFinite(value)
                        && value >= 0.0
                        && value <= 1.0);
    }

    private <T> T decode(String json, Class<T> type, String modelName) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new ModelUnavailableException(modelName + "模型参数无法读取，请重新训练");
        }
    }
}
