package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 使用全部已知历史逐位置拟合正态分布，滚动回测不读取目标期及未来开奖。
 *
 */
public class DecayedFrequencyModelTrainer {

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
                        calculator.score(history, NumberZone.FRONT),
                        calculator.score(history, NumberZone.BACK)));
            }
        }
        if (periods.isEmpty()) {
            throw new ModelUnavailableException("没有可用于回测的历史正态分布期次");
        }

        RollingPredictionSet validation = new RollingPredictionSet(List.copyOf(periods));
        GaussianZonePrediction finalFront = calculator.fit(draws, NumberZone.FRONT);
        GaussianZonePrediction finalBack = calculator.fit(draws, NumberZone.BACK);
        DecayedFrequencyArtifact artifact = new DecayedFrequencyArtifact(
                2,
                draws.size(),
                finalFront.distributions(),
                finalBack.distributions(),
                finalFront.scores().values(),
                finalBack.scores().values());
        return new TrainedDecayedFrequencyModel(
                artifact,
                validation,
                PredictionMetricsCalculator.calculate(draws, validation),
                periods.size() * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount()));
    }
}
