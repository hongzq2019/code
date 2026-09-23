package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VectorTransitionPredictorTest {

    @Test
    void similarSourceContributesItsSuccessorInsteadOfItself() {
        List<Draw> draws = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(20, 21, 22, 23, 24), List.of(8, 9)),
                draw(4, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(5, List.of(30, 31, 32, 33, 34), List.of(10, 12)));

        CandidateScores scores = new VectorTransitionPredictor()
                .predict(draws, 4, NumberZone.FRONT, 30);

        assertThat(PredictionMetricsCalculator.topNumbers(scores.values(), 5))
                .containsExactly(6, 7, 8, 9, 10);
        assertThat(scores.values().get(1)).isZero();
    }

    @Test
    void zeroSimilarityDoesNotProduceRandomFallback() {
        List<Draw> draws = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(20, 21, 22, 23, 24), List.of(8, 9)),
                draw(4, List.of(30, 31, 32, 33, 34), List.of(10, 12)));

        assertThatThrownBy(() -> new VectorTransitionPredictor()
                .predict(draws, 3, NumberZone.FRONT, 30))
                .isInstanceOf(ModelUnavailableException.class);
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2026, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }
}
