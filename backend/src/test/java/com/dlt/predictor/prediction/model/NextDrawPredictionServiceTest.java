package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private static final VectorTransitionParameters FRONT_PARAMETERS = new VectorTransitionParameters(15, 0.25, 1);
    private static final VectorTransitionParameters BACK_PARAMETERS = new VectorTransitionParameters(60, 0.75, 20);

    @Test
    void legacyPersistedCalibrationFlagsDoNotOverrideTheMeanHitCriterion() throws Exception {
        Fixture fixture = fixture(decayedArtifact(frontProbabilities(), backProbabilities()));

        PredictionResponse response = fixture.service().predict();

        assertThat(response.predictions()).allSatisfy(prediction -> {
            assertThat(prediction.frontBeatsBaseline()).isTrue();
            assertThat(prediction.backBeatsBaseline()).isTrue();
        });
    }

    @Test
    void returnsFrequencySimilarityAsFifthPredictionWithCompleteScores() throws Exception {
        Fixture fixture = fixture(decayedArtifact(frontProbabilities(), backProbabilities()));

        PredictionResponse response = fixture.service().predict();

        assertThat(response.predictions()).extracting(ModelPredictionResponse::type)
                .containsExactly(
                        PredictionModelType.LOGISTIC_REGRESSION,
                        PredictionModelType.VECTOR_TRANSITION,
                        PredictionModelType.DYNAMIC_ENSEMBLE,
                        PredictionModelType.DECAYED_FREQUENCY,
                        PredictionModelType.FREQUENCY_SIMILARITY);
        ModelPredictionResponse probability = response.predictions().get(3);
        assertThat(probability.name()).isEqualTo("历史正态分布");
        assertThat(probability.numbers().front()).containsExactly(31, 32, 33, 34, 35);
        assertThat(probability.numbers().back()).containsExactly(11, 12);
        assertThat(probability.frontScores()).hasSize(35);
        assertThat(probability.backScores()).hasSize(12);
        assertThat(probability.ensembleWeights()).isNull();
        ModelPredictionResponse frequencySimilarity = response.predictions().get(4);
        assertThat(frequencySimilarity.name()).isEqualTo("高频相似融合");
        assertThat(frequencySimilarity.numbers().front()).containsExactly(31, 32, 33, 34, 35);
        assertThat(frequencySimilarity.numbers().back()).containsExactly(11, 12);
        assertThat(frequencySimilarity.frontScores()).hasSize(35);
        assertThat(frequencySimilarity.backScores()).hasSize(12);
        assertThat(frequencySimilarity.ensembleWeights()).isNull();
        assertThat(response.predictions().get(1).numbers().front()).containsExactly(1, 2, 3, 4, 5);
        assertThat(response.predictions().get(1).numbers().back()).containsExactly(1, 2);
        verify(fixture.vectorPredictor()).predictLatest(
                anyList(), eq(NumberZone.FRONT), eq(FRONT_PARAMETERS));
        verify(fixture.vectorPredictor()).predictLatest(
                anyList(), eq(NumberZone.BACK), eq(BACK_PARAMETERS));
    }

    @Test
    void rejectsPredictionsFromAModelWhoseTrainingWaterlineIsOutdated() throws Exception {
        Fixture fixture = fixture(decayedArtifact(frontProbabilities(), backProbabilities()));
        when(fixture.drawStore().allChronological()).thenReturn(draws(21));

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("最新开奖");
    }

    @ParameterizedTest
    @MethodSource("invalidGaussianArtifacts")
    void rejectsLegacyOrDamagedHistoricalNormalArtifacts(DecayedFrequencyArtifact gaussian) throws Exception {
        Fixture fixture = fixture(gaussian);

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("历史正态分布模型参数");
    }

    private static Stream<DecayedFrequencyArtifact> invalidGaussianArtifacts() {
        List<GaussianPosition> invalid = new ArrayList<>(frontDistributions());
        invalid.set(0, new GaussianPosition(31, 0));
        Map<Integer, Double> mismatched = frontProbabilities();
        mismatched.put(1, 0.5);
        return Stream.of(
                new DecayedFrequencyArtifact(1, 20, List.of(), List.of(), frontProbabilities(), backProbabilities()),
                new DecayedFrequencyArtifact(2, 19, frontDistributions(), backDistributions(),
                        frontProbabilities(), backProbabilities()),
                new DecayedFrequencyArtifact(2, 20, invalid, backDistributions(),
                        frontProbabilities(), backProbabilities()),
                new DecayedFrequencyArtifact(2, 20, frontDistributions(), backDistributions(),
                        mismatched, backProbabilities()));
    }

    @ParameterizedTest
    @MethodSource("invalidArtifacts")
    void rejectsIncompleteNonFiniteOrOutOfRangeProbabilityArtifacts(
            Map<Integer, Double> front,
            Map<Integer, Double> back) throws Exception {
        Fixture fixture = fixture(decayedArtifact(front, back));

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("历史正态分布模型参数");
    }

    @Test
    void rejectsLegacyVectorArtifactUntilTheModelIsRetrained() throws Exception {
        Fixture fixture = fixture(
                decayedArtifact(frontProbabilities(), backProbabilities()),
                Map.of("artifactVersion", 2, "neighborCount", 30,
                        "frontBandwidth", 0.5, "backBandwidth", 0.75,
                        "vectorVersion", "dlt-zone-v1", "indexedThroughDrawId", 20L));

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("重新训练");
    }

    @ParameterizedTest
    @MethodSource("invalidVectorArtifacts")
    void rejectsIncompleteOrInvalidVectorParameters(VectorTransitionArtifact vector) throws Exception {
        Fixture fixture = fixture(decayedArtifact(frontProbabilities(), backProbabilities()), vector);

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("向量转移模型参数");
    }

    private static Stream<VectorTransitionArtifact> invalidVectorArtifacts() {
        return Stream.of(
                new VectorTransitionArtifact(3, null, BACK_PARAMETERS, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, FRONT_PARAMETERS, null, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, new VectorTransitionParameters(0, 0.5, 1),
                        BACK_PARAMETERS, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, new VectorTransitionParameters(30, Double.NaN, 1),
                        BACK_PARAMETERS, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, new VectorTransitionParameters(30, 0.5, -1),
                        BACK_PARAMETERS, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, new VectorTransitionParameters(30, 0.5, 0),
                        BACK_PARAMETERS, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, new VectorTransitionParameters(30, 0.5, 5, 29),
                        BACK_PARAMETERS, "dlt-zone-v1", 20L),
                new VectorTransitionArtifact(3, FRONT_PARAMETERS, BACK_PARAMETERS, "unknown", 20L),
                new VectorTransitionArtifact(3, FRONT_PARAMETERS, BACK_PARAMETERS, "dlt-zone-v1", 19L));
    }

    @Test
    void rejectsFrequencySimilarityArtifactWithMismatchedDependencies() throws Exception {
        Fixture fixture = fixture(
                decayedArtifact(frontProbabilities(), backProbabilities()),
                vectorArtifact(),
                new FrequencySimilarityArtifact(1, 0.5, 0.5, "other-decayed", "vector-1"));

        assertThatThrownBy(() -> fixture.service().predict())
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("高频相似融合模型引用的基础模型版本不一致");
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
        return fixture(
                decayed,
                vectorArtifact(),
                new FrequencySimilarityArtifact(1, 1.0, 1.0, "decayed-1", "vector-1"));
    }

    private static Fixture fixture(DecayedFrequencyArtifact decayed, Object vector)
            throws JsonProcessingException {
        return fixture(
                decayed,
                vector,
                new FrequencySimilarityArtifact(1, 1.0, 1.0, "decayed-1", "vector-1"));
    }

    private static Fixture fixture(
            DecayedFrequencyArtifact decayed,
            Object vector,
            FrequencySimilarityArtifact frequencySimilarity) throws JsonProcessingException {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        VectorTransitionPredictor vectorPredictor = mock(VectorTransitionPredictor.class);
        List<Draw> history = draws(20);
        when(drawStore.allChronological()).thenReturn(history);
        when(modelStore.activeBatch()).thenReturn(Optional.of(List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, "logistic-1",
                        new LogisticModelArtifact(2, 20, new double[7], new double[7])),
                snapshot(PredictionModelType.VECTOR_TRANSITION, "vector-1",
                        vector),
                snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, "ensemble-1",
                        new EnsembleArtifact(1, 0.5, 0.5, "logistic-1", "vector-1")),
                snapshot(PredictionModelType.DECAYED_FREQUENCY, "decayed-1", decayed),
                snapshot(PredictionModelType.FREQUENCY_SIMILARITY,
                        "frequency-similarity-1", frequencySimilarity))));
        when(vectorPredictor.predictLatest(anyList(), eq(NumberZone.FRONT), eq(FRONT_PARAMETERS)))
                .thenReturn(candidateScores(35, List.of(1, 2, 3, 4, 5)));
        when(vectorPredictor.predictLatest(anyList(), eq(NumberZone.BACK), eq(BACK_PARAMETERS)))
                .thenReturn(candidateScores(12, List.of(1, 2)));
        return new Fixture(new NextDrawPredictionService(
                drawStore, modelStore, vectorPredictor, OBJECT_MAPPER), vectorPredictor, drawStore);
    }

    private static VectorTransitionArtifact vectorArtifact() {
        return new VectorTransitionArtifact(3, FRONT_PARAMETERS, BACK_PARAMETERS, "dlt-zone-v1", 20L);
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
        return new DecayedFrequencyArtifact(2, 20, frontDistributions(), backDistributions(), front, back);
    }

    private static Map<Integer, Double> frontProbabilities() {
        return new LinkedHashMap<>(new DecayedFrequencyCalculator()
                .predict(frontDistributions(), NumberZone.FRONT).values());
    }

    private static Map<Integer, Double> backProbabilities() {
        return new LinkedHashMap<>(new DecayedFrequencyCalculator()
                .predict(backDistributions(), NumberZone.BACK).values());
    }

    private static List<GaussianPosition> frontDistributions() {
        return IntStream.rangeClosed(31, 35).mapToObj(number -> new GaussianPosition(number, 1)).toList();
    }

    private static List<GaussianPosition> backDistributions() {
        return List.of(new GaussianPosition(11, 1), new GaussianPosition(12, 1));
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
                false, false);
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

    private record Fixture(NextDrawPredictionService service, VectorTransitionPredictor vectorPredictor,
            DrawStore drawStore) {
    }
}
