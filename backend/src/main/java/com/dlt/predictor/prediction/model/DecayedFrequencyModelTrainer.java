package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 使用无未来数据的滚动窗口回测历史衰减概率模型。
 *
 */
public class DecayedFrequencyModelTrainer {

    public static final int HALF_LIFE_DRAWS = 200;

    private final DecayedFrequencyCalculator calculator;

    public DecayedFrequencyModelTrainer() {
        this(new DecayedFrequencyCalculator());
    }

    DecayedFrequencyModelTrainer(DecayedFrequencyCalculator calculator) {
        this.calculator = calculator;
    }

    public TrainedDecayedFrequencyModel train(List<Draw> draws, List<RollingFold> folds) {
        List<RollingPredictionSet.PeriodPrediction> periods = new ArrayList<>();
        for (RollingFold fold : folds) {
            for (int target = fold.validationFrom(); target < fold.validationUntil(); target++) {
                List<Draw> history = draws.subList(0, target);
                periods.add(new RollingPredictionSet.PeriodPrediction(
                        target,
                        calculator.score(history, NumberZone.FRONT, HALF_LIFE_DRAWS),
                        calculator.score(history, NumberZone.BACK, HALF_LIFE_DRAWS)));
            }
        }
        if (periods.isEmpty()) {
            throw new ModelUnavailableException("没有可用于回测的历史衰减概率期次");
        }

        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(periods));
        CandidateScores finalFront = calculator.score(draws, NumberZone.FRONT, HALF_LIFE_DRAWS);
        CandidateScores finalBack = calculator.score(draws, NumberZone.BACK, HALF_LIFE_DRAWS);
        DecayedFrequencyArtifact artifact = new DecayedFrequencyArtifact(
                1,
                HALF_LIFE_DRAWS,
                finalFront.values(),
                finalBack.values());
        return new TrainedDecayedFrequencyModel(
                artifact,
                validation,
                PredictionMetricsCalculator.calculate(draws, validation),
                periods.size() * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount()));
    }
}
