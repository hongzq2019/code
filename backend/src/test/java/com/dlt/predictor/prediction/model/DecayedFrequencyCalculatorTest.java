package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DecayedFrequencyCalculatorTest {

    private final DecayedFrequencyCalculator calculator = new DecayedFrequencyCalculator();

    @Test
    void historicalOrderDoesNotChangeTheFittedDistribution() {
        List<Draw> history = history();
        List<Draw> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);

        assertThat(calculator.fit(history, NumberZone.FRONT))
                .isEqualTo(calculator.fit(reversed, NumberZone.FRONT));
    }

    @Test
    void fitsEachPositionAcrossAllHistoryAndGeneratesTheMostLikelyOrderedCombination() {
        GaussianZonePrediction front = calculator.fit(history(), NumberZone.FRONT);
        GaussianZonePrediction back = calculator.fit(history(), NumberZone.BACK);

        assertThat(front.distributions()).extracting(GaussianPosition::mean).containsExactly(2.0, 8.0, 14.0, 20.0, 26.0);
        assertThat(front.distributions()).extracting(GaussianPosition::standardDeviation).containsOnly(1.0);
        assertThat(front.scores().selectedNumbers()).containsExactly(2, 8, 14, 20, 26);
        assertThat(back.distributions()).extracting(GaussianPosition::mean).containsExactly(3.0, 9.0);
        assertThat(back.scores().selectedNumbers()).containsExactly(3, 9);
    }

    @Test
    void estimatesTheStandardDeviationInsteadOfUsingOneFixedWidth() {
        GaussianZonePrediction fit = calculator.fit(List.of(
                draw(1, List.of(1, 7, 13, 19, 25), List.of(1, 7)),
                draw(2, List.of(5, 11, 17, 23, 29), List.of(5, 11))), NumberZone.FRONT);

        assertThat(fit.distributions()).extracting(GaussianPosition::standardDeviation).containsOnly(2.0);
    }

    @ParameterizedTest
    @EnumSource(NumberZone.class)
    void producesCompleteFiniteMarginalProbabilitiesWithTheCorrectTotal(NumberZone zone) {
        CandidateScores scores = calculator.score(history(), zone);

        assertThat(scores.values()).hasSize(zone.candidateCount());
        assertThat(scores.values().values()).allMatch(value -> Double.isFinite(value) && value >= 0 && value <= 1);
        assertThat(scores.values().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(zone.selectionCount(), within(1.0e-10));
        assertThat(scores.selectedNumbers()).hasSize(zone.selectionCount()).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void enforcesDistinctNumbersWhenPositionDistributionsHaveTheSamePeak() {
        CandidateScores scores = calculator.predict(
                List.of(new GaussianPosition(6, 1), new GaussianPosition(6, 1)), NumberZone.BACK);

        assertThat(scores.selectedNumbers()).containsExactly(5, 6);
    }

    @Test
    void orderedMarginalsAndBestCombinationMatchExhaustiveEnumeration() {
        CandidateScores result = calculator.predict(List.of(
                new GaussianPosition(3.2, 1.7), new GaussianPosition(8.4, 2.2)), NumberZone.BACK);
        double[] occurrenceWeight = new double[13];
        double total = 0;
        double bestWeight = -1;
        List<Integer> best = List.of();
        for (int first = 1; first < 12; first++) {
            for (int second = first + 1; second <= 12; second++) {
                double weight = Math.exp(-0.5 * (Math.pow((first - 3.2) / 1.7, 2)
                        + Math.pow((second - 8.4) / 2.2, 2)));
                total += weight;
                occurrenceWeight[first] += weight;
                occurrenceWeight[second] += weight;
                if (weight > bestWeight) {
                    bestWeight = weight;
                    best = List.of(first, second);
                }
            }
        }

        assertThat(result.selectedNumbers()).isEqualTo(best);
        for (int number = 1; number <= 12; number++) {
            assertThat(result.values().get(number)).isCloseTo(occurrenceWeight[number] / total, within(1.0e-12));
        }
    }

    @Test
    void identicalHistoricalDrawsStillProduceAValidDistribution() {
        GaussianZonePrediction result = calculator.fit(
                List.of(draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2))), NumberZone.FRONT);

        assertThat(result.distributions()).extracting(GaussianPosition::standardDeviation).containsOnly(1.0);
        assertThat(result.scores().selectedNumbers()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void rejectsEmptyHistoryAndInvalidDistributions() {
        assertThatThrownBy(() -> calculator.score(List.of(), NumberZone.FRONT))
                .isInstanceOf(ModelUnavailableException.class);
        assertThatThrownBy(() -> calculator.predict(
                List.of(new GaussianPosition(Double.NaN, 1), new GaussianPosition(8, 1)), NumberZone.BACK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.predict(
                List.of(new GaussianPosition(3, 0), new GaussianPosition(8, 1)), NumberZone.BACK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Draw> history() {
        return List.of(
                draw(1, List.of(1, 7, 13, 19, 25), List.of(2, 8)),
                draw(2, List.of(3, 9, 15, 21, 27), List.of(4, 10)));
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2026, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }
}
