package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用全部历史逐位置拟合离散正态分布，求最可能的递增组合及每个号码的边际概率。 */
public final class DecayedFrequencyCalculator {

    public static final double MINIMUM_STANDARD_DEVIATION = 1.0;

    public CandidateScores score(List<Draw> history, NumberZone zone) {
        return fit(history, zone).scores();
    }

    public GaussianZonePrediction fit(List<Draw> history, NumberZone zone) {
        if (history == null || history.isEmpty()) {
            throw new ModelUnavailableException("没有可用于历史正态分布统计的开奖记录");
        }
        int count = zone.selectionCount();
        double[] sums = new double[count];
        double[] squaredSums = new double[count];
        for (Draw draw : history) {
            List<Integer> numbers = zone == NumberZone.FRONT ? draw.numbers().front() : draw.numbers().back();
            for (int position = 0; position < count; position++) {
                double value = numbers.get(position);
                sums[position] += value;
                squaredSums[position] += value * value;
            }
        }
        List<GaussianPosition> distributions = new ArrayList<>();
        for (int position = 0; position < count; position++) {
            double mean = sums[position] / history.size();
            double variance = Math.max(0, squaredSums[position] / history.size() - mean * mean);
            distributions.add(new GaussianPosition(mean,
                    Math.max(MINIMUM_STANDARD_DEVIATION, Math.sqrt(variance))));
        }
        return new GaussianZonePrediction(distributions, predict(distributions, zone));
    }

    public CandidateScores predict(List<GaussianPosition> distributions, NumberZone zone) {
        validate(distributions, zone);
        int positions = zone.selectionCount();
        int maximum = zone.candidateCount();
        double[][] weights = new double[positions][maximum + 1];
        double[][] forward = new double[positions][maximum + 1];
        double[][] suffix = new double[positions][maximum + 1];
        double[][] best = new double[positions][maximum + 1];
        int[][] previous = new int[positions][maximum + 1];
        for (int position = 0; position < positions; position++) {
            Arrays.fill(best[position], Double.NEGATIVE_INFINITY);
            GaussianPosition distribution = distributions.get(position);
            for (int number = position + 1; number <= maximum - (positions - position - 1); number++) {
                double z = (number - distribution.mean()) / distribution.standardDeviation();
                double logWeight = -0.5 * z * z;
                weights[position][number] = Math.exp(logWeight);
                if (position == 0) {
                    forward[position][number] = weights[position][number];
                    best[position][number] = logWeight;
                } else {
                    double prefix = 0;
                    for (int before = 1; before < number; before++) {
                        prefix += forward[position - 1][before];
                        double candidate = best[position - 1][before] + logWeight;
                        if (candidate > best[position][number]) {
                            best[position][number] = candidate;
                            previous[position][number] = before;
                        }
                    }
                    forward[position][number] = weights[position][number] * prefix;
                }
            }
        }
        for (int number = 1; number <= maximum; number++) {
            suffix[positions - 1][number] = 1;
        }
        for (int position = positions - 2; position >= 0; position--) {
            for (int number = 1; number <= maximum; number++) {
                for (int after = number + 1; after <= maximum; after++) {
                    suffix[position][number] += weights[position + 1][after] * suffix[position + 1][after];
                }
            }
        }
        double normalizer = Arrays.stream(forward[positions - 1]).sum();
        if (!Double.isFinite(normalizer) || normalizer <= 0) {
            throw new ModelUnavailableException("历史正态分布无法形成有效有序组合，请重新训练");
        }
        Map<Integer, Double> probabilities = new LinkedHashMap<>();
        int last = positions;
        for (int number = 1; number <= maximum; number++) {
            double probability = 0;
            for (int position = 0; position < positions; position++) {
                probability += forward[position][number] * suffix[position][number] / normalizer;
            }
            probabilities.put(number, Math.max(0, Math.min(1, probability)));
            if (best[positions - 1][number] > best[positions - 1][last]) {
                last = number;
            }
        }
        List<Integer> selected = new ArrayList<>();
        for (int position = positions - 1; position >= 0; position--) {
            selected.add(last);
            last = previous[position][last];
        }
        Collections.reverse(selected);
        return new CandidateScores(probabilities, selected);
    }

    private static void validate(List<GaussianPosition> distributions, NumberZone zone) {
        if (distributions == null || distributions.size() != zone.selectionCount()) {
            throw new IllegalArgumentException("正态分布位置数与分区选号数不一致");
        }
        for (int position = 0; position < distributions.size(); position++) {
            GaussianPosition distribution = distributions.get(position);
            if (distribution == null || !Double.isFinite(distribution.mean())
                    || distribution.mean() < position + 1
                    || distribution.mean() > zone.candidateCount() - (zone.selectionCount() - position - 1)
                    || !Double.isFinite(distribution.standardDeviation())
                    || distribution.standardDeviation() < MINIMUM_STANDARD_DEVIATION) {
                throw new IllegalArgumentException("正态分布均值或标准差无效");
            }
        }
    }
}
