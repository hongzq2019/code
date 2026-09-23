package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * 分别为前区和后区搜索逻辑回归与向量转移模型的最优融合权重。
 *
 */
public class DynamicEnsembleTrainer {

    public TrainedEnsembleModel train(
            List<Draw> draws,
            RollingPredictionSet logistic,
            RollingPredictionSet vector,
            String logisticModelVersion,
            String vectorModelVersion) {
        List<AlignedPeriod> aligned = align(logistic, vector);
        if (aligned.isEmpty()) {
            throw new ModelUnavailableException("基础模型没有共同的验证期次");
        }
        double frontWeight = chooseWeight(draws, aligned, NumberZone.FRONT);
        double backWeight = chooseWeight(draws, aligned, NumberZone.BACK);
        List<RollingPredictionSet.PeriodPrediction> periods = aligned.stream()
                .map(period -> new RollingPredictionSet.PeriodPrediction(
                        period.targetIndex(),
                        combineScores(period.logistic().front(), period.vector().front(), frontWeight),
                        combineScores(period.logistic().back(), period.vector().back(), backWeight)))
                .toList();
        RollingPredictionSet validation = new RollingPredictionSet(periods);
        ModelMetrics metrics = PredictionMetricsCalculator.calculate(draws, validation);
        return new TrainedEnsembleModel(
                new EnsembleArtifact(1, frontWeight, backWeight, logisticModelVersion, vectorModelVersion),
                validation,
                metrics,
                metrics.frontBeatsBaseline() && metrics.backBeatsBaseline());
    }

    public CandidateScores combine(CandidateScores logistic, CandidateScores vector, double logisticWeight) {
        return combineScores(logistic, vector, logisticWeight);
    }

    private static double chooseWeight(List<Draw> draws, List<AlignedPeriod> periods, NumberZone zone) {
        ZonePerformance logisticPerformance = performance(draws, periods, zone, 1.0);
        ZonePerformance vectorPerformance = performance(draws, periods, zone, 0.0);
        boolean logisticWeak = logisticPerformance.losesBothBaselines(zone);
        boolean vectorWeak = vectorPerformance.losesBothBaselines(zone);
        int firstStep = 0;
        int lastStep = 20;
        if (logisticWeak && !vectorWeak) {
            lastStep = 0;
        } else if (vectorWeak && !logisticWeak) {
            firstStep = 20;
        }

        ZonePerformance best = null;
        double bestWeight = 0;
        for (int step = firstStep; step <= lastStep; step++) {
            double weight = step / 20.0;
            ZonePerformance candidate = performance(draws, periods, zone, weight);
            if (best == null || candidate.loss() < best.loss() - 1.0e-12
                    || (Math.abs(candidate.loss() - best.loss()) <= 1.0e-12
                    && (candidate.hits() > best.hits() + 1.0e-12
                    || (Math.abs(candidate.hits() - best.hits()) <= 1.0e-12 && weight > bestWeight)))) {
                best = candidate;
                bestWeight = weight;
            }
        }
        return bestWeight;
    }

    private static ZonePerformance performance(
            List<Draw> draws, List<AlignedPeriod> periods, NumberZone zone, double logisticWeight) {
        double loss = 0;
        double hits = 0;
        for (AlignedPeriod period : periods) {
            CandidateScores logistic = zone == NumberZone.FRONT
                    ? period.logistic().front() : period.logistic().back();
            CandidateScores vector = zone == NumberZone.FRONT
                    ? period.vector().front() : period.vector().back();
            CandidateScores combined = combineScores(logistic, vector, logisticWeight);
            List<Integer> actual = zone == NumberZone.FRONT
                    ? draws.get(period.targetIndex()).numbers().front()
                    : draws.get(period.targetIndex()).numbers().back();
            loss += PredictionMetricsCalculator.logLoss(combined.values(), actual, zone.candidateCount());
            hits += PredictionMetricsCalculator.topNumbers(combined.values(), zone.selectionCount()).stream()
                    .filter(actual::contains).count();
        }
        return new ZonePerformance(loss / periods.size(), hits / periods.size());
    }

    private static CandidateScores combineScores(
            CandidateScores logistic, CandidateScores vector, double logisticWeight) {
        Map<Integer, Double> combined = new LinkedHashMap<>();
        logistic.values().forEach((number, score) -> combined.put(
                number,
                logisticWeight * score + (1.0 - logisticWeight) * vector.values().getOrDefault(number, 0.0)));
        return new CandidateScores(combined);
    }

    private static List<AlignedPeriod> align(RollingPredictionSet logistic, RollingPredictionSet vector) {
        Map<Integer, RollingPredictionSet.PeriodPrediction> vectors = new HashMap<>();
        vector.periods().forEach(period -> vectors.put(period.targetIndex(), period));
        List<AlignedPeriod> aligned = new ArrayList<>();
        for (RollingPredictionSet.PeriodPrediction period : logistic.periods()) {
            RollingPredictionSet.PeriodPrediction vectorPeriod = vectors.get(period.targetIndex());
            if (vectorPeriod != null) {
                aligned.add(new AlignedPeriod(period.targetIndex(), period, vectorPeriod));
            }
        }
        return aligned;
    }

    private record AlignedPeriod(
            int targetIndex,
            RollingPredictionSet.PeriodPrediction logistic,
            RollingPredictionSet.PeriodPrediction vector) {
    }

    private record ZonePerformance(double loss, double hits) {
        boolean losesBothBaselines(NumberZone zone) {
            double probability = zone.selectionCount() / (double) zone.candidateCount();
            double baselineLoss = -(probability * Math.log(probability)
                    + (1 - probability) * Math.log(1 - probability));
            double baselineHits = zone.selectionCount() * zone.selectionCount()
                    / (double) zone.candidateCount();
            return loss >= baselineLoss && hits <= baselineHits;
        }
    }
}
