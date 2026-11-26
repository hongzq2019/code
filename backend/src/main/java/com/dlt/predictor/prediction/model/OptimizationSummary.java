package com.dlt.predictor.prediction.model;

import java.util.List;
import java.util.Map;

/** 零窗口表示全历史；所有验收指标都来自未参与本轮选参的尾部期数。 */
public record OptimizationSummary(
        List<Integer> evaluatedWindows,
        int selectedWindow,
        int developmentDrawCount,
        int tuningPeriodCount,
        int holdoutPeriodCount,
        String holdoutFromIssueNo,
        String holdoutThroughIssueNo,
        Map<PredictionModelType, ModelMetrics> controlMetrics,
        List<String> rejectionReasons,
        String stopReason,
        double frontVectorEnsembleOverlap,
        double backVectorEnsembleOverlap) {

    public OptimizationSummary {
        evaluatedWindows = List.copyOf(evaluatedWindows);
        controlMetrics = Map.copyOf(controlMetrics);
        rejectionReasons = List.copyOf(rejectionReasons);
    }
}
