package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicEnsembleTrainerTest {

    @Test
    void selectsFrontAndBackWeightsIndependentlyAndDeterministically() {
        Draw actual = new Draw(21L, "TARGET", LocalDate.of(2026, 1, 21),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "test");
        List<Draw> draws = java.util.Collections.nCopies(20, actual);
        draws = new java.util.ArrayList<>(draws);
        draws.add(actual);
        RollingPredictionSet logistic = set(scores(35, List.of(1, 2, 3, 4, 5), 0.9), scores(12, List.of(8, 9), 0.9));
        RollingPredictionSet vector = set(scores(35, List.of(20, 21, 22, 23, 24), 0.9), scores(12, List.of(1, 2), 0.9));

        TrainedEnsembleModel result = new DynamicEnsembleTrainer()
                .train(draws, logistic, vector, "logistic-1", "vector-1");

        assertThat(result.artifact().frontLogisticWeight()).isEqualTo(1.0);
        assertThat(result.artifact().backLogisticWeight()).isEqualTo(0.0);
        assertThat(result.artifact().frontLogisticWeight() + result.artifact().frontVectorWeight()).isEqualTo(1.0);
        assertThat(result.artifact().backLogisticWeight() + result.artifact().backVectorWeight()).isEqualTo(1.0);
    }

    private static RollingPredictionSet set(CandidateScores front, CandidateScores back) {
        return new RollingPredictionSet(List.of(new RollingPredictionSet.PeriodPrediction(20, front, back)));
    }

    private static CandidateScores scores(int count, List<Integer> high, double probability) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (int number = 1; number <= count; number++) {
            scores.put(number, high.contains(number) ? probability : 0.1);
        }
        return new CandidateScores(scores);
    }
}
