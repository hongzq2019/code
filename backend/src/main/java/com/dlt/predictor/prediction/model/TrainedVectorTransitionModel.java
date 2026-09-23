package com.dlt.predictor.prediction.model;

/**
 *
 * 向量转移模型的一次训练结果。
 *
 */
public record TrainedVectorTransitionModel(
        VectorTransitionArtifact artifact,
        RollingPredictionSet validationPredictions,
        ModelMetrics metrics,
        int sampleCount) {
}
