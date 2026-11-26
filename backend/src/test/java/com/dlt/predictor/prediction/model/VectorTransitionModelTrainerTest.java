package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VectorTransitionModelTrainerTest {

    private static final VectorTransitionParameters FRONT_BEST = new VectorTransitionParameters(15, 0.25, 1);
    private static final VectorTransitionParameters BACK_BEST = new VectorTransitionParameters(60, 0.75, 20);

    @Test
    void choosesMoreHitsEvenWhenAnotherParameterHasBetterLogLoss() {
        VectorTransitionPredictor predictor = new VectorTransitionPredictor() {
            @Override
            public List<CandidateScores> predictCandidates(List<Draw> draws, int targetIndex,
                    NumberZone zone, List<VectorTransitionParameters> parameters) {
                return parameters.stream().map(parameter -> {
                    boolean hitWinner = parameter.equals(FRONT_BEST);
                    Map<Integer, Double> values = new LinkedHashMap<>();
                    for (int number = 1; number <= zone.candidateCount(); number++) {
                        values.put(number, hitWinner ? (number <= zone.selectionCount() ? 0.99 : 0.98)
                                : (number > zone.selectionCount() && number <= 2 * zone.selectionCount() ? 0.2 : 0.1));
                    }
                    return new CandidateScores(values);
                }).toList();
            }
        };

        TrainedVectorTransitionModel result = new VectorTransitionModelTrainer(predictor)
                .train(repeatedDraws(3), List.of(new RollingFold(1, 1, 3)));

        assertThat(result.artifact().frontParameters()).isEqualTo(FRONT_BEST);
        assertThat(result.metrics().frontMeanHits()).isEqualTo(2.5);
    }

    @Test
    void doesNotUseTheCurrentOutcomeToChooseItsValidationParameters() {
        VectorTransitionModelTrainer trainer = new VectorTransitionModelTrainer(new ParameterPredictor());

        TrainedVectorTransitionModel result = trainer.train(
                repeatedDraws(2), List.of(new RollingFold(1, 1, 2)));

        // No earlier validation labels exist, so this target must use the default configuration.
        assertThat(PredictionMetricsCalculator.topNumbers(
                result.validationPredictions().periods().getFirst().front().values(), 5))
                .containsExactly(6, 7, 8, 9, 10);
        assertThat(PredictionMetricsCalculator.topNumbers(
                result.validationPredictions().periods().getFirst().back().values(), 2))
                .containsExactly(3, 4);
        // After observing that target, production can independently choose the best parameters.
        assertThat(result.artifact().artifactVersion()).isEqualTo(3);
        assertThat(result.artifact().frontParameters()).isEqualTo(FRONT_BEST);
        assertThat(result.artifact().backParameters()).isEqualTo(BACK_BEST);
    }

    @Test
    void reportsMetricsFromCausalPredictionsInsteadOfTheRetrospectiveWinner() {
        VectorTransitionModelTrainer trainer = new VectorTransitionModelTrainer(new ParameterPredictor());

        TrainedVectorTransitionModel result = trainer.train(
                repeatedDraws(3), List.of(new RollingFold(1, 1, 3)));

        assertThat(PredictionMetricsCalculator.topNumbers(
                result.validationPredictions().periods().get(1).front().values(), 5))
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(PredictionMetricsCalculator.topNumbers(
                result.validationPredictions().periods().get(1).back().values(), 2))
                .containsExactly(1, 2);
        assertThat(result.metrics().frontMeanHits()).isEqualTo(2.5);
        assertThat(result.metrics().backMeanHits()).isEqualTo(1.0);
        double firstFrontLoss = -(10 * Math.log(0.1) + 25 * Math.log(0.9)) / 35;
        double firstBackLoss = -(4 * Math.log(0.1) + 8 * Math.log(0.9)) / 12;
        assertThat(result.metrics().frontLogLoss())
                .isCloseTo((firstFrontLoss - Math.log(0.9)) / 2, within(1.0e-12));
        assertThat(result.metrics().backLogLoss())
                .isCloseTo((firstBackLoss - Math.log(0.9)) / 2, within(1.0e-12));
    }

    @Test
    void changingTheCurrentAndFutureDrawsCannotChangeEarlierOrCurrentValidationProbabilities() {
        List<Draw> original = new ArrayList<>();
        for (int index = 0; index < 26; index++) {
            int frontOffset = index * 7 % 35;
            int backOffset = index * 3 % 12;
            original.add(draw(index + 1,
                    IntStream.range(0, 5).map(n -> (frontOffset + n) % 35 + 1).boxed().sorted().toList(),
                    IntStream.range(0, 2).map(n -> (backOffset + n) % 12 + 1).boxed().sorted().toList()));
        }
        List<Draw> changed = new ArrayList<>(original);
        for (int index = 23; index < changed.size(); index++) {
            changed.set(index, draw(index + 1, List.of(29, 30, 31, 32, 33), List.of(10, 11)));
        }
        VectorTransitionModelTrainer trainer = new VectorTransitionModelTrainer();
        List<RollingFold> folds = List.of(new RollingFold(20, 20, 26));

        TrainedVectorTransitionModel first = trainer.train(original, folds);
        TrainedVectorTransitionModel second = trainer.train(changed, folds);

        assertThat(first.validationPredictions().periods()).hasSize(6);
        assertThat(second.validationPredictions().periods().subList(0, 4))
                .isEqualTo(first.validationPredictions().periods().subList(0, 4));
    }

    @Test
    void evaluatesOverlappingFoldsOnceInChronologicalOrder() {
        VectorTransitionModelTrainer trainer = new VectorTransitionModelTrainer(new ParameterPredictor());

        TrainedVectorTransitionModel result = trainer.train(repeatedDraws(7), List.of(
                new RollingFold(4, 4, 7), new RollingFold(2, 2, 5), new RollingFold(3, 3, 3)));

        assertThat(result.validationPredictions().periods())
                .extracting(RollingPredictionSet.PeriodPrediction::targetIndex)
                .containsExactly(2, 3, 4, 5, 6);
        assertThat(result.sampleCount()).isEqualTo(5 * 47);
    }

    @Test
    void keepsTheDefaultWhenAllParameterCandidatesTie() {
        VectorTransitionPredictor predictor = new VectorTransitionPredictor() {
            @Override
            public List<CandidateScores> predictCandidates(List<Draw> draws, int targetIndex,
                    NumberZone zone, List<VectorTransitionParameters> parameters) {
                return parameters.stream().map(parameter -> scores(zone, true)).toList();
            }
        };

        TrainedVectorTransitionModel result = new VectorTransitionModelTrainer(predictor)
                .train(repeatedDraws(3), List.of(new RollingFold(1, 1, 3)));

        assertThat(result.artifact().frontParameters()).isEqualTo(new VectorTransitionParameters(30, 0.5, 5));
        assertThat(result.artifact().backParameters()).isEqualTo(new VectorTransitionParameters(30, 0.5, 5));
    }

    @Test
    void rejectsInvalidOrEmptyValidationRanges() {
        VectorTransitionModelTrainer trainer = new VectorTransitionModelTrainer();
        List<Draw> draws = repeatedDraws(3);

        for (RollingFold fold : List.of(new RollingFold(0, 0, 2), new RollingFold(1, 1, 4),
                new RollingFold(2, 1, 2), new RollingFold(1, 2, 1))) {
            assertThatThrownBy(() -> trainer.train(draws, List.of(fold)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> trainer.train(draws, List.of()))
                .isInstanceOf(ModelUnavailableException.class);
        assertThatThrownBy(() -> trainer.train(draws, List.of(new RollingFold(1, 1, 1))))
                .isInstanceOf(ModelUnavailableException.class);
    }

    private static List<Draw> repeatedDraws(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> draw(id, List.of(1, 2, 3, 4, 5), List.of(1, 2))).toList();
    }

    private static Draw draw(long id, List<Integer> front, List<Integer> back) {
        return new Draw(id, "I" + id, LocalDate.of(2026, 1, 1).plusDays(id),
                DltNumbers.of(front, back), "test");
    }

    private static CandidateScores scores(NumberZone zone, boolean best) {
        List<Integer> high = best
                ? (zone == NumberZone.FRONT ? List.of(1, 2, 3, 4, 5) : List.of(1, 2))
                : (zone == NumberZone.FRONT ? List.of(6, 7, 8, 9, 10) : List.of(3, 4));
        Map<Integer, Double> scores = new LinkedHashMap<>();
        IntStream.rangeClosed(1, zone.candidateCount())
                .forEach(number -> scores.put(number, high.contains(number) ? 0.9 : 0.1));
        return new CandidateScores(scores);
    }

    private static final class ParameterPredictor extends VectorTransitionPredictor {
        @Override
        public List<CandidateScores> predictCandidates(List<Draw> draws, int targetIndex,
                NumberZone zone, List<VectorTransitionParameters> parameters) {
            VectorTransitionParameters best = zone == NumberZone.FRONT ? FRONT_BEST : BACK_BEST;
            return parameters.stream().map(parameter -> scores(zone, parameter.equals(best))).toList();
        }
    }
}
