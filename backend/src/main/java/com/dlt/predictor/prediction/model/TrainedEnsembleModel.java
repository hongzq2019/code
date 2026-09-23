package com.dlt.predictor.prediction.model;

/**
 *
 * 动态集成模型的一次训练结果。
 *
 */
public record TrainedEnsembleModel(
        EnsembleArtifact artifact,
        RollingPredictionSet validationPredictions,
        ModelMetrics metrics,
        boolean eligible) {
}
