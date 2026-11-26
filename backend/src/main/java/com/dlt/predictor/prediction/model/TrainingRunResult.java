package com.dlt.predictor.prediction.model;

import java.util.List;

public record TrainingRunResult(
        TrainingRunStatus status,
        String batchVersion,
        String message,
        List<ModelTrainingResult> models,
        OptimizationSummary optimization) {

    public TrainingRunResult(TrainingRunStatus status, String batchVersion, String message, List<ModelTrainingResult> models) {
        this(status, batchVersion, message, models, null);
    }

    public static TrainingRunResult status(TrainingRunStatus status, String message) {
        return new TrainingRunResult(status, null, message, List.of());
    }
}
