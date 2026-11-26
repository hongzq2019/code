package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DecayedFrequencyModelTrainerTest {

    @Test
    void validationPredictionIgnoresTargetAndFutureDraws() {
        List<Draw> original = draws(100);
        List<RollingFold> folds = new RollingFoldBuilder()
                .build(original.size(), TrainingDatasetBuilder.FEATURE_WINDOW);
        int firstTarget = folds.getFirst().validationFrom();

        TrainedDecayedFrequencyModel first = new DecayedFrequencyModelTrainer().train(original, folds);
        List<Draw> mutated = new ArrayList<>(original);
        mutated.set(firstTarget, draw(firstTarget + 1L,
                List.of(31, 32, 33, 34, 35), List.of(11, 12)));
        TrainedDecayedFrequencyModel second = new DecayedFrequencyModelTrainer().train(mutated, folds);

        RollingPredictionSet.PeriodPrediction firstPrediction = first.validationPredictions().periods().getFirst();
        RollingPredictionSet.PeriodPrediction secondPrediction = second.validationPredictions().periods().getFirst();
        assertThat(firstPrediction.targetIndex()).isEqualTo(firstTarget);
        assertThat(secondPrediction.front()).isEqualTo(firstPrediction.front());
        assertThat(secondPrediction.back()).isEqualTo(firstPrediction.back());
    }

    @Test
    void productionArtifactContainsAllHistoryPositionDistributionsAndCandidateProbabilities() {
        List<Draw> history = draws(100);
        List<RollingFold> folds = new RollingFoldBuilder()
                .build(history.size(), TrainingDatasetBuilder.FEATURE_WINDOW);

        TrainedDecayedFrequencyModel result = new DecayedFrequencyModelTrainer().train(history, folds);

        assertThat(result.artifact().artifactVersion()).isEqualTo(2);
        assertThat(result.artifact().historyDrawCount()).isEqualTo(100);
        assertThat(result.artifact().frontDistributions()).hasSize(5);
        assertThat(result.artifact().backDistributions()).hasSize(2);
        assertThat(result.artifact().frontProbabilities()).hasSize(35);
        assertThat(result.artifact().backProbabilities()).hasSize(12);
        assertThat(result.validationPredictions().periods()).hasSize(32);
        assertThat(result.sampleCount()).isEqualTo(32 * 47);
    }

    private static List<Draw> draws(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> draw(
                        index + 1L,
                        IntStream.range(0, 5).map(offset -> ((index * 5 + offset) % 35) + 1).boxed().toList(),
                        IntStream.range(0, 2).map(offset -> ((index * 2 + offset) % 12) + 1).boxed().toList()))
                .toList();
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2025, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }
}
