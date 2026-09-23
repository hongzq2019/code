package com.dlt.predictor.prediction.model;

public record ModelTrainingResult(
        PredictionModelType type,
        String modelVersion,
        int drawCount,
        int sampleCount,
        long trainedThroughDrawId,
        String trainedThroughIssueNo,
        ModelMetrics metrics) {

    public static ModelTrainingResult from(ModelSnapshot snapshot) {
        return new ModelTrainingResult(
                snapshot.type(), snapshot.modelVersion(), snapshot.drawCount(), snapshot.sampleCount(),
                snapshot.trainedThroughDrawId(), snapshot.trainedThroughIssueNo(), snapshot.metrics());
    }
}
