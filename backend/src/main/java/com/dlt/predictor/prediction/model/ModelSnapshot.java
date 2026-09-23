package com.dlt.predictor.prediction.model;

import java.time.Instant;
import java.time.LocalDate;

public record ModelSnapshot(
        PredictionModelType type,
        String modelVersion,
        String algorithmVersion,
        String batchVersion,
        PredictionModelStatus status,
        String configJson,
        int featureWindow,
        int drawCount,
        int sampleCount,
        long trainedThroughDrawId,
        String trainedThroughIssueNo,
        LocalDate trainedThroughDate,
        Instant trainedAt,
        ModelMetrics metrics) {

    public ModelSnapshot withStatus(PredictionModelStatus newStatus) {
        return new ModelSnapshot(type, modelVersion, algorithmVersion, batchVersion, newStatus,
                configJson, featureWindow, drawCount, sampleCount, trainedThroughDrawId,
                trainedThroughIssueNo, trainedThroughDate, trainedAt, metrics);
    }
}
