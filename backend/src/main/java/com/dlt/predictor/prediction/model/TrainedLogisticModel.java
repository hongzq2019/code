package com.dlt.predictor.prediction.model;

public record TrainedLogisticModel(
        LogisticModelArtifact artifact,
        RollingPredictionSet validationPredictions,
        ModelMetrics metrics,
        int sampleCount) {
}
