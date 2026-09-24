package com.dlt.predictor.prediction.model;

/**
 *
 * 历史衰减概率模型的一次训练与滚动验证结果。
 *
 */
public record TrainedDecayedFrequencyModel(
        DecayedFrequencyArtifact artifact,
        RollingPredictionSet validationPredictions,
        ModelMetrics metrics,
        int sampleCount) {
}
