package com.dlt.predictor.prediction.model;

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

    public static double logLoss(Map<Integer, Double> scores, List<Integer> actual, int candidates) {
        double total = 0;
        for (int number = 1; number <= candidates; number++) {
            double probability = Math.max(1.0e-12, Math.min(1.0 - 1.0e-12, scores.get(number)));
            total -= actual.contains(number) ? Math.log(probability) : Math.log(1.0 - probability);
        }
        return total / candidates;
    }
}
