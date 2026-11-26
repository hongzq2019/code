package com.dlt.predictor.prediction.model;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

final class OptimizationAcceptance {
    static List<String> rejectionReasons(Map<PredictionModelType, ModelMetrics> candidate,
            Map<PredictionModelType, ModelMetrics> control) {
        List<String> reasons = new ArrayList<>();
        boolean improved = false;
        for (PredictionModelType type : PredictionModelType.values()) {
            ModelMetrics value = candidate.get(type);
            ModelMetrics baseline = control.get(type);
            if (!finite(value) || !finite(baseline)) {
                reasons.add(type + " 的验收指标缺失或无效");
                continue;
            }
            if (value.frontMeanHits() <= 25.0 / 35 || value.backMeanHits() <= 4.0 / 12) {
                reasons.add(type + " 的前区或后区平均命中未超过随机基线");
            }
            if (value.frontMeanHits() < baseline.frontMeanHits() - 1.0e-12
                    || value.backMeanHits() < baseline.backMeanHits() - 1.0e-12) {
                reasons.add(type + " 的前区或后区平均命中低于全历史控制模型");
            }
            improved |= value.frontMeanHits() > baseline.frontMeanHits() + 1.0e-12
                    || value.backMeanHits() > baseline.backMeanHits() + 1.0e-12;
        }
        if (!improved) {
            reasons.add("相对全历史控制模型没有改善，停止本轮搜索");
        }
        return List.copyOf(reasons);
    }

    private static boolean finite(ModelMetrics metrics) {
        return metrics != null && Double.isFinite(metrics.frontMeanHits())
                && Double.isFinite(metrics.backMeanHits()) && Double.isFinite(metrics.frontLogLoss())
                && Double.isFinite(metrics.backLogLoss()) && metrics.frontMeanHits() >= 0
                && metrics.frontMeanHits() <= 5 && metrics.backMeanHits() >= 0 && metrics.backMeanHits() <= 2;
    }
}
