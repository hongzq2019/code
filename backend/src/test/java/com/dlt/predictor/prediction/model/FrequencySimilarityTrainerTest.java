package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrequencySimilarityTrainerTest {

    @Test
    void selectsFrequencyAndSimilarityWeightsIndependentlyFromRollingBacktest() {
        Draw actual = new Draw(21L, "TARGET", LocalDate.of(2026, 1, 21),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "test");
        List<Draw> draws = new ArrayList<>(java.util.Collections.nCopies(20, actual));
        draws.add(actual);
        RollingPredictionSet frequency = set(
                scores(35, List.of(1, 2, 3, 4, 5), 0.9),
                scores(12, List.of(8, 9), 0.9));
        RollingPredictionSet similarity = set(
                scores(35, List.of(20, 21, 22, 23, 24), 0.9),
                scores(12, List.of(1, 2), 0.9));

        TrainedFrequencySimilarityModel result = new FrequencySimilarityTrainer().train(
                draws, frequency, similarity, "decayed-1", "vector-1");

        assertThat(result.artifact().artifactVersion()).isEqualTo(1);
        assertThat(result.artifact().frontDecayedFrequencyWeight()).isEqualTo(1.0);
        assertThat(result.artifact().backDecayedFrequencyWeight()).isEqualTo(0.0);
        assertThat(result.artifact().decayedFrequencyModelVersion()).isEqualTo("decayed-1");
        assertThat(result.artifact().vectorModelVersion()).isEqualTo("vector-1");
        assertThat(result.artifact().frontDecayedFrequencyWeight()
                + result.artifact().frontVectorWeight()).isEqualTo(1.0);
        assertThat(result.artifact().backDecayedFrequencyWeight()
                + result.artifact().backVectorWeight()).isEqualTo(1.0);
        assertThat(result.sampleCount()).isEqualTo(47);
    }

    @Test
    void combinesCompleteFrequencyAndSimilarityScores() {
        CandidateScores frequency = scores(35, List.of(1, 2, 3, 4, 5), 0.9);
        CandidateScores similarity = scores(35, List.of(20, 21, 22, 23, 24), 0.9);

        CandidateScores combined = new FrequencySimilarityTrainer().combine(frequency, similarity, 0.75);

        assertThat(PredictionMetricsCalculator.topNumbers(combined.values(), 5))
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void reportsAllPeriodsUsingOnlyPreviouslyObservedOutcomesForWeightSelection() {
        List<Draw> draws = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            draws.add(draw(index + 1L, List.of(1, 2, 3, 4, 5), List.of(1, 2)));
        }
        draws.set(24, draw(25L, List.of(20, 21, 22, 23, 24), List.of(8, 9)));
        CandidateScores frequencyFront = scores(35, List.of(1, 2, 3, 4, 5), 0.9);
        CandidateScores frequencyBack = scores(12, List.of(1, 2), 0.9);
        CandidateScores similarityFront = scores(35, List.of(20, 21, 22, 23, 24), 0.9);
        CandidateScores similarityBack = scores(12, List.of(8, 9), 0.9);

        TrainedFrequencySimilarityModel result = new FrequencySimilarityTrainer().train(
                draws,
                set(List.of(20, 21, 22, 23, 24), frequencyFront, frequencyBack),
                set(List.of(20, 21, 22, 23, 24), similarityFront, similarityBack),
                "decayed-1", "vector-1");

        assertThat(result.validationPredictions().periods())
                .extracting(RollingPredictionSet.PeriodPrediction::targetIndex)
                .containsExactly(20, 21, 22, 23, 24);
        assertThat(result.validationPredictions().periods().getFirst().front().values().get(1)).isEqualTo(0.5);
        assertThat(result.validationPredictions().periods().getLast().front().values().get(1)).isEqualTo(0.9);
        assertThat(result.validationPredictions().periods().getLast().back().values().get(1)).isEqualTo(0.9);
    }

    private static RollingPredictionSet set(CandidateScores front, CandidateScores back) {
        return new RollingPredictionSet(List.of(new RollingPredictionSet.PeriodPrediction(20, front, back)));
    }

    private static RollingPredictionSet set(
            List<Integer> targetIndexes, CandidateScores front, CandidateScores back) {
        return new RollingPredictionSet(targetIndexes.stream()
                .map(targetIndex -> new RollingPredictionSet.PeriodPrediction(targetIndex, front, back))
                .toList());
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2026, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }

    private static CandidateScores scores(int count, List<Integer> high, double probability) {
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (int number = 1; number <= count; number++) {
            scores.put(number, high.contains(number) ? probability : 0.1);
        }
        return new CandidateScores(scores);
    }
}
