package com.dlt.predictor.prediction.dto;

import com.dlt.predictor.prediction.model.ModelMetrics;

public record ModelMetricsResponse(
        double frontLogLoss,
        double backLogLoss,
        double frontMeanHits,
        double backMeanHits,
        double frontBaselineLogLoss,
        double backBaselineLogLoss,
        double frontBaselineHits,
        double backBaselineHits) {

    public static ModelMetricsResponse from(ModelMetrics metrics) {
        return new ModelMetricsResponse(
                metrics.frontLogLoss(), metrics.backLogLoss(),
                metrics.frontMeanHits(), metrics.backMeanHits(),
                metrics.frontBaselineLogLoss(), metrics.backBaselineLogLoss(),
                metrics.frontBaselineHits(), metrics.backBaselineHits());
    }
}
