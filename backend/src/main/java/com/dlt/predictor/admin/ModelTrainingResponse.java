package com.dlt.predictor.admin;

import com.dlt.predictor.prediction.model.ModelTrainingResult;
import com.dlt.predictor.prediction.model.TrainingRunResult;
import com.dlt.predictor.prediction.model.TrainingRunStatus;
import java.util.List;

public record ModelTrainingResponse(
        TrainingRunStatus status,
        String batchVersion,
        String message,
        List<ModelTrainingResult> models) {

    static ModelTrainingResponse from(TrainingRunResult result) {
        return new ModelTrainingResponse(
                result.status(), result.batchVersion(), result.message(), result.models());
    }
}
