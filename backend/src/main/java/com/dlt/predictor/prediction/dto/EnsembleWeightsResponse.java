package com.dlt.predictor.prediction.dto;

public record EnsembleWeightsResponse(
        double frontLogisticWeight,
        double frontVectorWeight,
        double backLogisticWeight,
        double backVectorWeight,
        String logisticModelVersion,
        String vectorModelVersion) {
}
