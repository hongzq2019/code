package com.dlt.predictor.prediction.model;

/**
 *
 * 高频历史正态分布与高斯向量相似度模型的融合权重及依赖版本。
 *
 */
public record FrequencySimilarityArtifact(
        int artifactVersion,
        double frontDecayedFrequencyWeight,
        double backDecayedFrequencyWeight,
        String decayedFrequencyModelVersion,
        String vectorModelVersion) {

    public double frontVectorWeight() {
        return 1.0 - frontDecayedFrequencyWeight;
    }

    public double backVectorWeight() {
        return 1.0 - backDecayedFrequencyWeight;
    }
}
