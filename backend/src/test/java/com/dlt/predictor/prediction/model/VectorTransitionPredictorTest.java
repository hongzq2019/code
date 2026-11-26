package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class VectorTransitionPredictorTest {

    @Test
    void includesAllEquallySimilarSourcesAtTheNeighborBoundary() {
        List<Draw> history = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(4, List.of(20, 21, 22, 23, 24), List.of(8, 9)),
                draw(5, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(6, List.of(30, 31, 32, 33, 34), List.of(10, 12)));

        CandidateScores scores = new VectorTransitionPredictor().predict(history, 5, NumberZone.FRONT, 1);

        assertThat(scores.values().get(6)).isEqualTo(0.5);
        assertThat(scores.values().get(20)).isEqualTo(0.5);
    }

    @Test
    void productionAndBacktestUseExactlyTheSameKnownHistory() {
        List<Draw> history = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(1, 2, 3, 4, 5), List.of(1, 2)));
        List<Draw> withFuture = new ArrayList<>(history);
        withFuture.add(draw(4, List.of(30, 31, 32, 33, 34), List.of(10, 12)));
        VectorTransitionPredictor predictor = new VectorTransitionPredictor();

        assertThat(predictor.predictLatest(history, NumberZone.FRONT, 30, 0.5))
                .isEqualTo(predictor.predict(withFuture, 3, NumberZone.FRONT, 30, 0.5));
    }

    @Test
    void smoothsSparseNeighborsWithThePersistedPriorStrength() {
        List<Draw> history = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(1, 2, 3, 4, 5), List.of(1, 2)));

        CandidateScores scores = new VectorTransitionPredictor().predictLatest(
                history, NumberZone.FRONT, new VectorTransitionParameters(30, 0.5, 1));

        assertThat(scores.values().get(6)).isCloseTo(4.0 / 7, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(scores.values().get(1)).isCloseTo(1.0 / 14, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(scores.values().values()).allMatch(p -> p > 0 && p < 1);
        assertThat(scores.values().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(5.0, org.assertj.core.data.Offset.offset(1.0e-12));
    }

    @ParameterizedTest
    @EnumSource(NumberZone.class)
    void usesDeterministicUniformPriorWhenThereAreNoSimilarTransitions(NumberZone zone) {
        List<Draw> history = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(20, 21, 22, 23, 24), List.of(8, 9)));

        CandidateScores scores = new VectorTransitionPredictor().predictLatest(
                history, zone, new VectorTransitionParameters(30, 0.5, 5));

        assertThat(scores.values()).hasSize(zone.candidateCount());
        assertThat(scores.values().values()).allMatch(p ->
                Math.abs(p - zone.selectionCount() / (double) zone.candidateCount()) < 1.0e-12);
    }

    @Test
    void candidateSearchMatchesIndividualScoringWithoutReadingFutureDraws() {
        List<Draw> draws = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(4, List.of(30, 31, 32, 33, 34), List.of(10, 12)));
        List<VectorTransitionParameters> parameters = List.of(
                new VectorTransitionParameters(15, 0.25, 1),
                new VectorTransitionParameters(60, 0.75, 20));
        VectorTransitionPredictor predictor = new VectorTransitionPredictor();

        List<CandidateScores> candidates = predictor.predictCandidates(draws, 3, NumberZone.FRONT, parameters);

        for (int index = 0; index < parameters.size(); index++) {
            assertThat(candidates.get(index)).isEqualTo(predictor.predictLatest(
                    draws.subList(0, 3), NumberZone.FRONT, parameters.get(index)));
        }
    }

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

    @Test
    void weightsSimilarSuccessorsWithGaussianKernel() {
        List<Draw> draws = List.of(
                draw(1, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(2, List.of(6, 7, 8, 9, 10), List.of(3, 4)),
                draw(3, List.of(1, 11, 12, 13, 14), List.of(5, 6)),
                draw(4, List.of(20, 21, 22, 23, 24), List.of(7, 8)),
                draw(5, List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                draw(6, List.of(30, 31, 32, 33, 34), List.of(9, 10)));

        CandidateScores scores = new VectorTransitionPredictor()
                .predict(draws, 5, NumberZone.FRONT, 30);

        assertThat(scores.values().get(6)).isCloseTo(0.9608342772, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(scores.values().get(20)).isCloseTo(0.0391657228, org.assertj.core.data.Offset.offset(1.0e-9));
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2026, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }
}
