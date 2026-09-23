package com.dlt.predictor.prediction.model;

public record ModelMetrics(
        double frontLogLoss,
        double backLogLoss,
        double frontMeanHits,
        double backMeanHits,
        double frontBaselineLogLoss,
        double backBaselineLogLoss,
        double frontBaselineHits,
        double backBaselineHits,
        boolean frontBeatsBaseline,
        boolean backBeatsBaseline) {
}
