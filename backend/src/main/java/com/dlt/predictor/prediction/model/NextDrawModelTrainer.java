package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * 使用扩展时间窗口回测并训练生产逻辑回归参数。
 *
 */
public class NextDrawModelTrainer {

    private final TrainingDatasetBuilder datasetBuilder = new TrainingDatasetBuilder();
    private final LogisticRegressionTrainer regressionTrainer = new LogisticRegressionTrainer();

    public TrainedLogisticModel train(List<Draw> draws, List<RollingFold> folds) {
        return train(draws, folds, 0);
    }

    public TrainedLogisticModel train(List<Draw> draws, List<RollingFold> folds, int trainingWindow) {
        List<RollingPredictionSet.PeriodPrediction> periods = new ArrayList<>();
        for (RollingFold fold : folds) {
            int trainingFrom = targetFrom(fold.trainingUntil(), trainingWindow);
            double[] frontWeights = regressionTrainer.train(
                    datasetBuilder.samples(draws, NumberZone.FRONT,
                            trainingFrom, fold.trainingUntil()),
                    NumberZone.FRONT);
            double[] backWeights = regressionTrainer.train(
                    datasetBuilder.samples(draws, NumberZone.BACK,
                            trainingFrom, fold.trainingUntil()),
                    NumberZone.BACK);
            for (int target = fold.validationFrom(); target < fold.validationUntil(); target++) {
                periods.add(new RollingPredictionSet.PeriodPrediction(
                        target,
                        score(draws.subList(0, target), NumberZone.FRONT, frontWeights),
                        score(draws.subList(0, target), NumberZone.BACK, backWeights)));
            }
        }

        int targetFrom = targetFrom(draws.size(), trainingWindow);
        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(periods));
        ModelMetrics metrics = PredictionMetricsCalculator.calculate(draws, validation);
        int sampleCount = (draws.size() - targetFrom)
                * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount());
        return new TrainedLogisticModel(
                fitLatest(draws, trainingWindow),
                validation,
                metrics,
                sampleCount);
    }

    public CandidateScores scoreLatest(List<Draw> history, NumberZone zone, double[] weights) {
        return score(history, zone, weights);
    }

    public LogisticModelArtifact fitLatest(List<Draw> draws, int trainingWindow) {
        int from = targetFrom(draws.size(), trainingWindow);
        return new LogisticModelArtifact(3, TrainingDatasetBuilder.FEATURE_WINDOW,
                regressionTrainer.train(datasetBuilder.samples(draws, NumberZone.FRONT, from, draws.size()), NumberZone.FRONT),
                regressionTrainer.train(datasetBuilder.samples(draws, NumberZone.BACK, from, draws.size()), NumberZone.BACK),
                trainingWindow, TrainingHistoryFingerprint.calculate(draws));
    }

    private static int targetFrom(int historyCount, int trainingWindow) {
        if (historyCount <= TrainingDatasetBuilder.FEATURE_WINDOW || trainingWindow < 0
                || (trainingWindow != 0 && (trainingWindow <= TrainingDatasetBuilder.FEATURE_WINDOW
                || trainingWindow > historyCount))) {
            throw new IllegalArgumentException("逻辑回归训练窗口必须为全历史或至少 21 期，且不能超过已知历史");
        }
        return (trainingWindow == 0 ? 0 : historyCount - trainingWindow) + TrainingDatasetBuilder.FEATURE_WINDOW;
    }

    private CandidateScores score(List<Draw> history, NumberZone zone, double[] weights) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        datasetBuilder.latestFeatures(history, zone)
                .forEach((number, features) -> scores.put(number, regressionTrainer.predict(weights, features)));
        return new CandidateScores(scores);
    }
}
