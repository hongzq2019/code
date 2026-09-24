package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecayedFrequencyCalculatorTest {

    private final DecayedFrequencyCalculator calculator = new DecayedFrequencyCalculator();

    @Test
    void usesConfiguredHalfLifeAndNormalizesWeightedOccurrences() {
        assertThat(calculator.weight(0, 200)).isEqualTo(1.0);
        assertThat(calculator.weight(200, 200)).isCloseTo(0.5, within(1.0e-12));

        List<Draw> history = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)));
        CandidateScores scores = calculator.score(history, NumberZone.FRONT, 1);

        assertThat(scores.values()).hasSize(35).containsKeys(1, 35);
        assertThat(scores.values().get(1)).isCloseTo(1.0 / 3.0, within(1.0e-12));
        assertThat(scores.values().get(6)).isCloseTo(2.0 / 3.0, within(1.0e-12));
    }

    @Test
    void returnsCompleteBackMapAndStableNumberTieOrdering() {
        List<Draw> history = List.of(draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)));

        CandidateScores front = calculator.score(history, NumberZone.FRONT, 200);
        CandidateScores back = calculator.score(history, NumberZone.BACK, 200);

        assertThat(back.values()).hasSize(12).containsKeys(1, 12);
        assertThat(PredictionMetricsCalculator.topNumbers(front.values(), 5))
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(PredictionMetricsCalculator.topNumbers(back.values(), 2))
                .containsExactly(1, 2);
    }

    @Test
    void rejectsEmptyHistoryAndInvalidHalfLife() {
        assertThatThrownBy(() -> calculator.score(List.of(), NumberZone.FRONT, 200))
                .isInstanceOf(ModelUnavailableException.class);
        assertThatThrownBy(() -> calculator.score(
                List.of(draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2))),
                NumberZone.FRONT,
                0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2026, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }
}
