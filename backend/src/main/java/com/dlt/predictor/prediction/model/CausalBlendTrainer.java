package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 两个基础模型的逐期融合：先预测，再更新评估；选参优先命中数，其次概率误差。 */
final class CausalBlendTrainer {

    private static final int STEPS = 20;
    private static final double EPSILON = 1.0e-12;

    Result train(List<Draw> draws, RollingPredictionSet left, RollingPredictionSet right,
            boolean preferDifferentFromLeft) {
        Map<Integer, RollingPredictionSet.PeriodPrediction> rights = index(right);
        ZoneState front = new ZoneState(NumberZone.FRONT, preferDifferentFromLeft);
        ZoneState back = new ZoneState(NumberZone.BACK, preferDifferentFromLeft);
        List<RollingPredictionSet.PeriodPrediction> predictions = new ArrayList<>();
        for (RollingPredictionSet.PeriodPrediction period : index(left).values()) {
            RollingPredictionSet.PeriodPrediction other = rights.get(period.targetIndex());
            if (other == null) {
                continue;
            }
            if (period.targetIndex() < 0 || period.targetIndex() >= draws.size()) {
                throw new IllegalArgumentException("融合回测期次超出开奖记录范围");
            }
            predictions.add(new RollingPredictionSet.PeriodPrediction(period.targetIndex(),
                    combine(period.front(), other.front(), front.weight()),
                    combine(period.back(), other.back(), back.weight())));
            Draw actual = draws.get(period.targetIndex());
            front.observe(period.front(), other.front(), actual.numbers().front());
            back.observe(period.back(), other.back(), actual.numbers().back());
        }
        if (predictions.isEmpty()) {
            throw new ModelUnavailableException("基础模型没有共同的验证期次");
        }
        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(predictions));
        return new Result(front.weight(), back.weight(), validation,
                PredictionMetricsCalculator.calculate(draws, validation));
    }

    static CandidateScores combine(CandidateScores left, CandidateScores right, double weight) {
        if (!Double.isFinite(weight) || weight < 0 || weight > 1) {
            throw new IllegalArgumentException("融合权重必须位于 [0, 1]");
        }
        Map<Integer, Double> values = new TreeMap<>();
        left.values().forEach((number, value) -> values.put(number, weight * value));
        right.values().forEach((number, value) -> values.merge(number, (1 - weight) * value, Double::sum));
        return new CandidateScores(values);
    }

    private static Map<Integer, RollingPredictionSet.PeriodPrediction> index(RollingPredictionSet predictions) {
        Map<Integer, RollingPredictionSet.PeriodPrediction> result = new TreeMap<>();
        for (RollingPredictionSet.PeriodPrediction period : predictions.periods()) {
            if (result.putIfAbsent(period.targetIndex(), period) != null) {
                throw new IllegalArgumentException("融合回测期次不能重复");
            }
        }
        return result;
    }

    record Result(double frontWeight, double backWeight, RollingPredictionSet validation, ModelMetrics metrics) {
    }

    private static final class ZoneState {
        private final NumberZone zone;
        private final boolean preferDifferentFromLeft;
        private final double[] losses = new double[STEPS + 1];
        private final int[] hits = new int[STEPS + 1];
        private final int[] overlaps = new int[STEPS + 1];

        ZoneState(NumberZone zone, boolean preferDifferentFromLeft) {
            this.zone = zone;
            this.preferDifferentFromLeft = preferDifferentFromLeft;
        }

        double weight() {
            int best = STEPS / 2;
            for (int candidate = 0; candidate <= STEPS; candidate++) {
                if (better(candidate, best)) {
                    best = candidate;
                }
            }
            return best / (double) STEPS;
        }

        private boolean better(int candidate, int best) {
            if (hits[candidate] != hits[best]) {
                return hits[candidate] > hits[best];
            }
            if (Math.abs(losses[candidate] - losses[best]) > EPSILON) {
                return losses[candidate] < losses[best];
            }
            if (preferDifferentFromLeft && overlaps[candidate] != overlaps[best]) {
                return overlaps[candidate] < overlaps[best];
            }
            return Math.abs(candidate - STEPS / 2) < Math.abs(best - STEPS / 2);
        }

        void observe(CandidateScores left, CandidateScores right, List<Integer> actual) {
            List<Integer> leftTop = PredictionMetricsCalculator.topNumbers(left.values(), zone.selectionCount());
            for (int step = 0; step <= STEPS; step++) {
                CandidateScores scores = combine(left, right, step / (double) STEPS);
                List<Integer> top = PredictionMetricsCalculator.topNumbers(scores.values(), zone.selectionCount());
                losses[step] += PredictionMetricsCalculator.logLoss(scores.values(), actual, zone.candidateCount());
                hits[step] += (int) top.stream().filter(actual::contains).count();
                overlaps[step] += (int) top.stream().filter(leftTop::contains).count();
            }
        }
    }
}
