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
        List<RollingPredictionSet.PeriodPrediction> periods = new ArrayList<>();
        for (RollingFold fold : folds) {
            double[] frontWeights = regressionTrainer.train(
                    datasetBuilder.samples(draws, NumberZone.FRONT,
                            TrainingDatasetBuilder.FEATURE_WINDOW, fold.trainingUntil()),
                    NumberZone.FRONT);
            double[] backWeights = regressionTrainer.train(
                    datasetBuilder.samples(draws, NumberZone.BACK,
                            TrainingDatasetBuilder.FEATURE_WINDOW, fold.trainingUntil()),
                    NumberZone.BACK);
            for (int target = fold.validationFrom(); target < fold.validationUntil(); target++) {
                periods.add(new RollingPredictionSet.PeriodPrediction(
                        target,
                        score(draws.subList(0, target), NumberZone.FRONT, frontWeights),
                        score(draws.subList(0, target), NumberZone.BACK, backWeights)));
            }
        }

        int targetFrom = TrainingDatasetBuilder.FEATURE_WINDOW;
        double[] finalFront = regressionTrainer.train(
                datasetBuilder.samples(draws, NumberZone.FRONT, targetFrom, draws.size()),
                NumberZone.FRONT);
        double[] finalBack = regressionTrainer.train(
                datasetBuilder.samples(draws, NumberZone.BACK, targetFrom, draws.size()),
                NumberZone.BACK);
        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(periods));
        ModelMetrics metrics = PredictionMetricsCalculator.calculate(draws, validation);
        int sampleCount = (draws.size() - targetFrom)
                * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount());
        return new TrainedLogisticModel(
                new LogisticModelArtifact(1, TrainingDatasetBuilder.FEATURE_WINDOW, finalFront, finalBack),
                validation,
                metrics,
                sampleCount);
    }

    public CandidateScores scoreLatest(List<Draw> history, NumberZone zone, double[] weights) {
        return score(history, zone, weights);
    }

    private CandidateScores score(List<Draw> history, NumberZone zone, double[] weights) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        datasetBuilder.latestFeatures(history, zone)
                .forEach((number, features) -> scores.put(number, regressionTrainer.predict(weights, features)));
        return new CandidateScores(scores);
    }
}
