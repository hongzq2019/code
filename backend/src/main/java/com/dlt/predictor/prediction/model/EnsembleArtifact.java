package com.dlt.predictor.prediction.model;

/**
 *
 * 动态集成模型的前后区逻辑回归权重及依赖模型版本。
 *
 */
public record EnsembleArtifact(
        int artifactVersion,
        double frontLogisticWeight,
        double backLogisticWeight,
        String logisticModelVersion,
        String vectorModelVersion) {

    public double frontVectorWeight() {
        return 1.0 - frontLogisticWeight;
    }

    public double backVectorWeight() {
        return 1.0 - backLogisticWeight;
    }
}
