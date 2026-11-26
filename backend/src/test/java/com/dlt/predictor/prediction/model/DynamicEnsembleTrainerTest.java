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
    void prioritizesObservedTopNumberHitsOverSmallLogLossGains() {
        Draw actual = new Draw(1L, "I1", LocalDate.of(2026, 1, 1),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "test");
        List<Draw> draws = java.util.Collections.nCopies(21, actual);
        Map<Integer, Double> left = new LinkedHashMap<>();
        Map<Integer, Double> right = new LinkedHashMap<>();
        for (int number = 1; number <= 35; number++) {
            left.put(number, number <= 5 ? 0.9 : 0.8);
            right.put(number, number <= 5 ? 0.2 : number <= 10 ? 0.21 : 0.1);
        }
        DynamicEnsembleTrainer trainer = new DynamicEnsembleTrainer();
        CandidateScores back = scores(12, List.of(1, 2), 0.9);

        TrainedEnsembleModel result = trainer.train(draws,
                set(new CandidateScores(left), back), set(new CandidateScores(right), back), "l", "v");

        CandidateScores prediction = trainer.combine(new CandidateScores(left), new CandidateScores(right),
                result.artifact().frontLogisticWeight());
        assertThat(PredictionMetricsCalculator.topNumbers(prediction.values(), 5))
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void firstValidationPredictionDoesNotChooseWeightsUsingItsOwnOutcome() {
        Draw actual = new Draw(21L, "TARGET", LocalDate.of(2026, 1, 21),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "test");
        List<Draw> draws = new java.util.ArrayList<>(java.util.Collections.nCopies(21, actual));
        TrainedEnsembleModel result = new DynamicEnsembleTrainer().train(draws,
                set(scores(35, List.of(1, 2, 3, 4, 5), 0.9), scores(12, List.of(1, 2), 0.9)),
                set(scores(35, List.of(20, 21, 22, 23, 24), 0.9), scores(12, List.of(8, 9), 0.9)),
                "logistic-1", "vector-1");

        assertThat(result.validationPredictions().periods().getFirst().front().values().get(1)).isEqualTo(0.5);
        assertThat(result.validationPredictions().periods().getFirst().back().values().get(1)).isEqualTo(0.5);
    }

    @Test
    void changingFutureOutcomesCannotChangeEarlierEnsemblePredictions() {
        Draw actual = new Draw(21L, "TARGET", LocalDate.of(2026, 1, 21),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "test");
        List<Draw> original = new java.util.ArrayList<>(java.util.Collections.nCopies(25, actual));
        List<Draw> changed = new java.util.ArrayList<>(original);
        changed.set(23, new Draw(24L, "CHANGED", LocalDate.of(2026, 1, 24),
                DltNumbers.of(List.of(20, 21, 22, 23, 24), List.of(8, 9)), "test"));
        RollingPredictionSet left = new RollingPredictionSet(java.util.stream.IntStream.range(20, 25)
                .mapToObj(target -> new RollingPredictionSet.PeriodPrediction(target,
                        scores(35, List.of(1, 2, 3, 4, 5), 0.9), scores(12, List.of(1, 2), 0.9))).toList());
        RollingPredictionSet right = new RollingPredictionSet(java.util.stream.IntStream.range(20, 25)
                .mapToObj(target -> new RollingPredictionSet.PeriodPrediction(target,
                        scores(35, List.of(20, 21, 22, 23, 24), 0.9), scores(12, List.of(8, 9), 0.9))).toList());

        TrainedEnsembleModel first = new DynamicEnsembleTrainer().train(original, left, right, "l", "v");
        TrainedEnsembleModel second = new DynamicEnsembleTrainer().train(changed, left, right, "l", "v");

        assertThat(first.validationPredictions().periods()).hasSize(5);
        assertThat(second.validationPredictions().periods().subList(0, 4))
                .isEqualTo(first.validationPredictions().periods().subList(0, 4));
    }

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

    @Test
    void prefersLessOverlapWithLogisticWhenValidationPerformanceIsTied() {
        Draw actual = new Draw(21L, "TARGET", LocalDate.of(2026, 1, 21),
                DltNumbers.of(List.of(31, 32, 33, 34, 35), List.of(11, 12)), "test");
        List<Draw> draws = new java.util.ArrayList<>(java.util.Collections.nCopies(20, actual));
        draws.add(actual);
        CandidateScores logisticFront = nearUniformScores(35, List.of(1, 2, 3, 4, 5));
        CandidateScores vectorFront = nearUniformScores(35, List.of(6, 7, 8, 9, 10));
        CandidateScores back = scores(12, List.of(1, 2), 0.9);

        TrainedEnsembleModel result = new DynamicEnsembleTrainer().train(
                draws,
                set(logisticFront, back),
                set(vectorFront, back),
                "logistic-1",
                "vector-1");

        CandidateScores ensembleFront = new DynamicEnsembleTrainer().combine(
                logisticFront, vectorFront, result.artifact().frontLogisticWeight());
        assertThat(PredictionMetricsCalculator.topNumbers(ensembleFront.values(), 5))
                .containsExactly(6, 7, 8, 9, 10);
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

    private static CandidateScores nearUniformScores(int count, List<Integer> high) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (int number = 1; number <= count; number++) {
            scores.put(number, high.contains(number) ? 0.50000000000001 : 0.5);
        }
        return new CandidateScores(scores);
    }
}
