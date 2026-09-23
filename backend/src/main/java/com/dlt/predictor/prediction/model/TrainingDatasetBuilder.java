package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrainingDatasetBuilder {

    public static final int FEATURE_WINDOW = 20;

    public List<BinarySample> samples(
            List<Draw> draws, NumberZone zone, int targetFrom, int targetUntil) {
        if (targetFrom < FEATURE_WINDOW || targetUntil > draws.size() || targetFrom > targetUntil) {
            throw new IllegalArgumentException("训练目标范围无效");
        }
        List<BinarySample> samples = new ArrayList<>();
        for (int target = targetFrom; target < targetUntil; target++) {
            List<Integer> actual = numbers(draws.get(target), zone);
            for (int candidate = 1; candidate <= zone.candidateCount(); candidate++) {
                samples.add(new BinarySample(candidate, features(draws, target, candidate, zone),
                        actual.contains(candidate) ? 1 : 0));
            }
        }
        return List.copyOf(samples);
    }

    public Map<Integer, double[]> latestFeatures(List<Draw> draws, NumberZone zone) {
        if (draws.size() < FEATURE_WINDOW) {
            throw new IllegalArgumentException("至少需要 20 期开奖构造特征");
        }
        Map<Integer, double[]> result = new LinkedHashMap<>();
        for (int candidate = 1; candidate <= zone.candidateCount(); candidate++) {
            result.put(candidate, features(draws, draws.size(), candidate, zone));
        }
        return result;
    }

    private double[] features(List<Draw> draws, int target, int candidate, NumberZone zone) {
        double previous = contains(draws.get(target - 1), candidate, zone) ? 1 : 0;
        double frequency5 = frequency(draws, target - 5, target, candidate, zone);
        double frequency10 = frequency(draws, target - 10, target, candidate, zone);
        double frequency20 = frequency(draws, target - 20, target, candidate, zone);
        int omission = FEATURE_WINDOW;
        for (int index = target - 1; index >= target - FEATURE_WINDOW; index--) {
            if (contains(draws.get(index), candidate, zone)) {
                omission = target - 1 - index;
                break;
            }
        }
        double previous15 = frequency(draws, target - 20, target - 5, candidate, zone);
        return new double[]{previous, frequency5, frequency10, frequency20,
                omission / (double) FEATURE_WINDOW, frequency5 - previous15};
    }

    private static double frequency(
            List<Draw> draws, int from, int until, int candidate, NumberZone zone) {
        long hits = draws.subList(from, until).stream()
                .filter(draw -> contains(draw, candidate, zone))
                .count();
        return hits / (double) (until - from);
    }

    private static boolean contains(Draw draw, int candidate, NumberZone zone) {
        return numbers(draw, zone).contains(candidate);
    }

    private static List<Integer> numbers(Draw draw, NumberZone zone) {
        return zone == NumberZone.FRONT ? draw.numbers().front() : draw.numbers().back();
    }
}
