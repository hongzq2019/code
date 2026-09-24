package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.dto.ModelPredictionResponse;
import com.dlt.predictor.prediction.dto.PredictionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NextDrawPredictionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void returnsDecayedFrequencyAsFourthPredictionWithCompleteScores() throws Exception {
        Fixture fixture = fixture(decayedArtifact(frontProbabilities(), backProbabilities()));

        PredictionResponse response = fixture.service().predict();

        assertThat(response.predictions()).extracting(ModelPredictionResponse::type)
                .containsExactly(
                        PredictionModelType.LOGISTIC_REGRESSION,
                        PredictionModelType.VECTOR_TRANSITION,
                        PredictionModelType.DYNAMIC_ENSEMBLE,
                        PredictionModelType.DECAYED_FREQUENCY);
        ModelPredictionResponse probability = response.predictions().get(3);
        assertThat(probability.name()).isEqualTo("历史衰减概率");
        assertThat(probability.numbers().front()).containsExactly(31, 32, 33, 34, 35);
        assertThat(probability.numbers().back()).containsExactly(11, 12);
        assertThat(probability.frontScores()).hasSize(35);
        assertThat(probability.backScores()).hasSize(12);
        assertThat(probability.ensembleWeights()).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidArtifacts")
    void rejectsIncompleteNonFiniteOrOutOfRangeProbabilityArtifacts(
            Map<Integer, Double> front,
            Map<Integer, Double> back) throws Exception {
        Fixture fixture = fixture(decayedArtifact(front, back));

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("历史衰减概率模型参数");
    }

    private static Stream<Arguments> invalidArtifacts() {
        Map<Integer, Double> missing = frontProbabilities();
        missing.remove(35);
        return Stream.of(
                Arguments.of(missing, backProbabilities()),
                Arguments.of(frontWith(1, Double.NaN), backProbabilities()),
                Arguments.of(frontWith(1, Double.POSITIVE_INFINITY), backProbabilities()),
                Arguments.of(frontWith(1, -0.01), backProbabilities()),
                Arguments.of(frontWith(1, 1.01), backProbabilities()));
    }

    private static Fixture fixture(DecayedFrequencyArtifact decayed) throws JsonProcessingException {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        VectorTransitionPredictor vectorPredictor = mock(VectorTransitionPredictor.class);
        List<Draw> history = draws(20);
        when(drawStore.allChronological()).thenReturn(history);
        when(modelStore.activeBatch()).thenReturn(Optional.of(List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, "logistic-1",
                        new LogisticModelArtifact(1, 20, new double[7], new double[7])),
                snapshot(PredictionModelType.VECTOR_TRANSITION, "vector-1",
                        new VectorTransitionArtifact(1, 30, "dlt-zone-v1", 20L)),
                snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, "ensemble-1",
                        new EnsembleArtifact(1, 0.5, 0.5, "logistic-1", "vector-1")),
                snapshot(PredictionModelType.DECAYED_FREQUENCY, "decayed-1", decayed))));
        when(vectorPredictor.predictLatest(anyList(), eq(NumberZone.FRONT), eq(30)))
                .thenReturn(candidateScores(35, List.of(1, 2, 3, 4, 5)));
        when(vectorPredictor.predictLatest(anyList(), eq(NumberZone.BACK), eq(30)))
                .thenReturn(candidateScores(12, List.of(1, 2)));
        return new Fixture(new NextDrawPredictionService(
                drawStore, modelStore, vectorPredictor, OBJECT_MAPPER));
    }

    private static ModelSnapshot snapshot(PredictionModelType type, String version, Object artifact)
            throws JsonProcessingException {
        return new ModelSnapshot(
                type, version, "algorithm-v1", "batch-1", PredictionModelStatus.ACTIVE,
                OBJECT_MAPPER.writeValueAsString(artifact), 20, 20, 100, 20L, "I20",
                LocalDate.of(2026, 1, 20), Instant.parse("2026-09-24T00:00:00Z"), metrics());
    }

    private static DecayedFrequencyArtifact decayedArtifact(
            Map<Integer, Double> front, Map<Integer, Double> back) {
        return new DecayedFrequencyArtifact(1, 200, front, back);
    }

    private static Map<Integer, Double> frontProbabilities() {
        Map<Integer, Double> values = probabilities(35);
        IntStream.rangeClosed(31, 35).forEach(number -> values.put(number, 0.9));
        return values;
    }

    private static Map<Integer, Double> backProbabilities() {
        Map<Integer, Double> values = probabilities(12);
        values.put(11, 0.9);
        values.put(12, 0.9);
        return values;
    }

    private static Map<Integer, Double> frontWith(int number, double value) {
        Map<Integer, Double> values = frontProbabilities();
        values.put(number, value);
        return values;
    }

    private static Map<Integer, Double> probabilities(int count) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        IntStream.rangeClosed(1, count).forEach(number -> values.put(number, 0.1));
        return values;
    }

    private static CandidateScores candidateScores(int count, List<Integer> high) {
        Map<Integer, Double> values = probabilities(count);
        high.forEach(number -> values.put(number, 0.8));
        return new CandidateScores(values);
    }

    private static ModelMetrics metrics() {
        return new ModelMetrics(
                0.4, 0.5, 0.8, 0.4,
                0.41, 0.51, 25.0 / 35, 4.0 / 12,
                true, true);
    }

    private static List<Draw> draws(int count) {
        List<Draw> draws = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int drawIndex = index;
            draws.add(new Draw(
                    index + 1L,
                    "I" + (index + 1),
                    LocalDate.of(2026, 1, 1).plusDays(index),
                    DltNumbers.of(
                            IntStream.range(0, 5)
                                    .map(offset -> ((drawIndex * 5 + offset) % 35) + 1).boxed().toList(),
                            IntStream.range(0, 2)
                                    .map(offset -> ((drawIndex * 2 + offset) % 12) + 1).boxed().toList()),
                    "test"));
        }
        return draws;
    }

    private record Fixture(NextDrawPredictionService service) {
    }
}
