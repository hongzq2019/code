package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按时间向前选参：当前验证期的参数只由此前验证期决定。
 * 线上参数使用全部已知回测期选择，报告指标来自逐期样本外预测。
 */
public class VectorTransitionModelTrainer {

    public static final String VECTOR_VERSION = "dlt-zone-v1";
    private static final double EPSILON = 1.0e-12;
    private static final List<VectorTransitionParameters> PARAMETER_CANDIDATES = parameterCandidates();

    private final VectorTransitionPredictor predictor;

    public VectorTransitionModelTrainer() {
        this(new VectorTransitionPredictor());
    }

    VectorTransitionModelTrainer(VectorTransitionPredictor predictor) {
        this.predictor = predictor;
    }

    public TrainedVectorTransitionModel train(List<Draw> draws, List<RollingFold> folds) {
        return train(draws, folds, 0);
    }

    public TrainedVectorTransitionModel train(List<Draw> draws, List<RollingFold> folds, int historyWindow) {
        if (historyWindow < 0 || historyWindow == 1) {
            throw new IllegalArgumentException("向量历史窗口必须为全历史或至少两期");
        }
        List<VectorTransitionParameters> candidates = PARAMETER_CANDIDATES.stream()
                .map(parameter -> new VectorTransitionParameters(parameter.neighborCount(), parameter.bandwidth(),
                        parameter.priorStrength(), historyWindow)).toList();
        List<Integer> targets = validationTargets(draws.size(), folds);
        if (targets.isEmpty()) {
            throw new ModelUnavailableException("没有可用于回测的向量转移期次");
        }
        Performance frontPerformance = new Performance(PARAMETER_CANDIDATES.size());
        Performance backPerformance = new Performance(PARAMETER_CANDIDATES.size());
        List<RollingPredictionSet.PeriodPrediction> periods = new ArrayList<>();
        for (int target : targets) {
            int frontChoice = frontPerformance.bestIndex();
            int backChoice = backPerformance.bestIndex();
            List<CandidateScores> fronts = predictor.predictCandidates(
                    draws, target, NumberZone.FRONT, candidates);
            List<CandidateScores> backs = predictor.predictCandidates(
                    draws, target, NumberZone.BACK, candidates);
            periods.add(new RollingPredictionSet.PeriodPrediction(
                    target, fronts.get(frontChoice), backs.get(backChoice)));
            // 当前开奖只能在该期预测保存后参与下一期选参。
            frontPerformance.observe(fronts, draws.get(target).numbers().front(), NumberZone.FRONT);
            backPerformance.observe(backs, draws.get(target).numbers().back(), NumberZone.BACK);
        }
        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(periods));
        return new TrainedVectorTransitionModel(
                new VectorTransitionArtifact(
                        3,
                        candidates.get(frontPerformance.bestIndex()),
                        candidates.get(backPerformance.bestIndex()),
                        VECTOR_VERSION,
                        draws.getLast().id()),
                validation,
                PredictionMetricsCalculator.calculate(draws, validation),
                periods.size() * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount()));
    }

    private static List<VectorTransitionParameters> parameterCandidates() {
        Set<VectorTransitionParameters> candidates = new LinkedHashSet<>();
        // 完全同分时保留适中的默认参数，避免无证据切换。
        candidates.add(new VectorTransitionParameters(30, 0.5, 5));
        for (int neighbors : List.of(15, 30, 60, 120)) {
            for (double bandwidth : List.of(0.25, 0.5, 0.75, 1.25)) {
                for (double priorStrength : List.of(1.0, 5.0, 20.0)) {
                    candidates.add(new VectorTransitionParameters(neighbors, bandwidth, priorStrength));
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static List<Integer> validationTargets(int drawCount, List<RollingFold> folds) {
        Set<Integer> targets = new TreeSet<>();
        for (RollingFold fold : folds) {
            if (fold.trainingUntil() < 1 || fold.trainingUntil() > fold.validationFrom()
                    || fold.validationFrom() < 1 || fold.validationUntil() < fold.validationFrom()
                    || fold.validationUntil() > drawCount) {
                throw new IllegalArgumentException("向量回测时间窗口无效");
            }
            for (int target = fold.validationFrom(); target < fold.validationUntil(); target++) {
                targets.add(target);
            }
        }
        return List.copyOf(targets);
    }

    private static final class Performance {
        private final double[] losses;
        private final int[] hits;

        Performance(int candidateCount) {
            losses = new double[candidateCount];
            hits = new int[candidateCount];
        }

        int bestIndex() {
            int best = 0;
            for (int candidate = 1; candidate < losses.length; candidate++) {
                if (hits[candidate] > hits[best]
                        || (hits[candidate] == hits[best] && losses[candidate] < losses[best] - EPSILON)) {
                    best = candidate;
                }
            }
            return best;
        }

        void observe(List<CandidateScores> predictions, List<Integer> actual, NumberZone zone) {
            for (int index = 0; index < predictions.size(); index++) {
                CandidateScores scores = predictions.get(index);
                losses[index] += PredictionMetricsCalculator.logLoss(
                        scores.values(), actual, zone.candidateCount());
                hits[index] += (int) PredictionMetricsCalculator.topNumbers(
                        scores.values(), zone.selectionCount()).stream().filter(actual::contains).count();
            }
        }
    }
}
