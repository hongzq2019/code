package com.dlt.predictor.prediction.model;

/**
 *
 * 高频相似融合模型的一次训练与滚动验证结果。
 *
 */
public record TrainedFrequencySimilarityModel(
        FrequencySimilarityArtifact artifact,
        RollingPredictionSet validationPredictions,
        ModelMetrics metrics,
        int sampleCount) {
}
