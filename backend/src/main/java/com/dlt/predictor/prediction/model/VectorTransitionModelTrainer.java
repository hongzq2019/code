package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 在滚动时间窗口内回测向量转移模型，并生成生产模型参数。
 *
 */
public class VectorTransitionModelTrainer {

    public static final int NEIGHBOR_COUNT = 30;
    public static final String VECTOR_VERSION = "dlt-zone-v1";

    private final VectorTransitionPredictor predictor;

    public VectorTransitionModelTrainer() {
        this(new VectorTransitionPredictor());
    }

    VectorTransitionModelTrainer(VectorTransitionPredictor predictor) {
        this.predictor = predictor;
    }

    public TrainedVectorTransitionModel train(List<Draw> draws, List<RollingFold> folds) {
        List<RollingPredictionSet.PeriodPrediction> periods = new ArrayList<>();
        for (RollingFold fold : folds) {
            for (int target = fold.validationFrom(); target < fold.validationUntil(); target++) {
                try {
                    periods.add(new RollingPredictionSet.PeriodPrediction(
                            target,
                            predictor.predict(draws, target, NumberZone.FRONT, NEIGHBOR_COUNT),
                            predictor.predict(draws, target, NumberZone.BACK, NEIGHBOR_COUNT)));
                } catch (ModelUnavailableException ignored) {
                    // 某个验证期没有正相似转移时不伪造随机分数，由其余有效期评估模型。
                }
            }
        }
        if (periods.isEmpty()) {
            throw new ModelUnavailableException("没有可用于回测的向量转移期次");
        }
        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(periods));
        long indexedThroughDrawId = draws.get(draws.size() - 1).id();
        return new TrainedVectorTransitionModel(
                new VectorTransitionArtifact(1, NEIGHBOR_COUNT, VECTOR_VERSION, indexedThroughDrawId),
                validation,
                PredictionMetricsCalculator.calculate(draws, validation),
                periods.size() * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount()));
    }
}
