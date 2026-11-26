package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.List;
import java.util.Map;

public final class PredictionMetricsCalculator {

    private PredictionMetricsCalculator() {
    }

    public static List<Integer> topNumbers(Map<Integer, Double> scores, int count) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(count)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    public static List<Integer> topNumbers(CandidateScores scores, int count) {
        if (!scores.selectedNumbers().isEmpty()) {
            if (scores.selectedNumbers().size() != count) {
                throw new IllegalArgumentException("显式选号数量与分区要求不一致");
            }
            return scores.selectedNumbers();
        }
        return topNumbers(scores.values(), count);
    }

    public static double logLoss(Map<Integer, Double> scores, List<Integer> actual, int candidates) {
        double total = 0;
        for (int number = 1; number <= candidates; number++) {
            double probability = Math.max(1.0e-12, Math.min(1.0 - 1.0e-12, scores.get(number)));
            total -= actual.contains(number) ? Math.log(probability) : Math.log(1.0 - probability);
        }
        return total / candidates;
    }

    public static ModelMetrics calculate(List<Draw> draws, RollingPredictionSet validation) {
        if (validation.periods().isEmpty()) {
            throw new IllegalArgumentException("验证预测不能为空");
        }
        double frontLoss = 0;
        double backLoss = 0;
        double frontHits = 0;
        double backHits = 0;
        for (RollingPredictionSet.PeriodPrediction period : validation.periods()) {
            Draw actual = draws.get(period.targetIndex());
            frontLoss += logLoss(period.front().values(), actual.numbers().front(), NumberZone.FRONT.candidateCount());
            backLoss += logLoss(period.back().values(), actual.numbers().back(), NumberZone.BACK.candidateCount());
            frontHits += topNumbers(period.front(), NumberZone.FRONT.selectionCount()).stream()
                    .filter(actual.numbers().front()::contains).count();
            backHits += topNumbers(period.back(), NumberZone.BACK.selectionCount()).stream()
                    .filter(actual.numbers().back()::contains).count();
        }
        int count = validation.periods().size();
        frontLoss /= count;
        backLoss /= count;
        frontHits /= count;
        backHits /= count;
        double frontBaselineLoss = baselineLogLoss(NumberZone.FRONT);
        double backBaselineLoss = baselineLogLoss(NumberZone.BACK);
        double frontBaselineHits = baselineHits(NumberZone.FRONT);
        double backBaselineHits = baselineHits(NumberZone.BACK);
        return new ModelMetrics(
                frontLoss, backLoss, frontHits, backHits,
                frontBaselineLoss, backBaselineLoss, frontBaselineHits, backBaselineHits,
                frontHits > frontBaselineHits,
                backHits > backBaselineHits);
    }

    private static double baselineLogLoss(NumberZone zone) {
        double probability = zone.selectionCount() / (double) zone.candidateCount();
        return -(probability * Math.log(probability) + (1 - probability) * Math.log(1 - probability));
    }

    private static double baselineHits(NumberZone zone) {
        return zone.selectionCount() * zone.selectionCount() / (double) zone.candidateCount();
    }
}
