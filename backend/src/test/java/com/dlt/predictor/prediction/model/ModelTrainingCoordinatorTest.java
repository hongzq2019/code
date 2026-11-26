package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

class ModelTrainingCoordinatorTest {

    @Test
    void requestsRetrainingWhenHistoricalNormalModelHasNotReplacedTheOldDecayAlgorithm() {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        List<Draw> draws = draws(100);
        List<ModelSnapshot> batch = new java.util.ArrayList<>(activeBatch(draws.getLast(), "vector-transition-hit-window-v4"));
        batch.set(3, snapshot(PredictionModelType.DECAYED_FREQUENCY, "old-frequency", "decayed-frequency-v1", draws.getLast()));
        when(drawStore.allChronological()).thenReturn(draws);
        when(modelStore.activeBatch()).thenReturn(Optional.of(batch));
        ModelTrainingCoordinator coordinator = new ModelTrainingCoordinator(drawStore, modelStore,
                new ObjectMapper(), executor, mock(NextDrawModelTrainer.class),
                mock(VectorTransitionModelTrainer.class), mock(DynamicEnsembleTrainer.class),
                mock(DecayedFrequencyModelTrainer.class), mock(FrequencySimilarityTrainer.class));

        assertThat(coordinator.requestIfStale()).isEqualTo(TrainingRunStatus.STARTED);
        verify(executor).execute(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vector-transition-gaussian-v2", "vector-transition-gaussian-v3", "vector-transition-hit-window-v4"})
    void onlyLatestVectorAlgorithmCanRemainCurrent(String vectorAlgorithm) {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        List<Draw> draws = draws(100);
        Draw latest = draws.getLast();
        when(drawStore.allChronological()).thenReturn(draws);
        when(modelStore.activeBatch()).thenReturn(Optional.of(activeBatch(latest, vectorAlgorithm)));
        ModelTrainingCoordinator coordinator = new ModelTrainingCoordinator(
                drawStore, modelStore, new ObjectMapper(), taskExecutor,
                mock(NextDrawModelTrainer.class), mock(VectorTransitionModelTrainer.class),
                mock(DynamicEnsembleTrainer.class), mock(DecayedFrequencyModelTrainer.class),
                mock(FrequencySimilarityTrainer.class));

        if (vectorAlgorithm.endsWith("v4")) {
            assertThat(coordinator.requestIfStale()).isEqualTo(TrainingRunStatus.SUCCESS);
            verify(taskExecutor, never()).execute(any());
        } else {
            assertThat(coordinator.requestIfStale()).isEqualTo(TrainingRunStatus.STARTED);
            verify(taskExecutor).execute(any());
        }
    }

    @Test
    void requestsRetrainingWhenFrequencySimilarityAlgorithmVersionIsOutdatedAtLatestWaterline() {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        List<Draw> draws = draws(100);
        Draw latest = draws.getLast();
        when(drawStore.allChronological()).thenReturn(draws);
        when(modelStore.activeBatch()).thenReturn(Optional.of(List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, "logistic-1", "logistic-multi-window-v3", latest),
                snapshot(PredictionModelType.VECTOR_TRANSITION, "vector-1", "vector-transition-hit-window-v4", latest),
                snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, "ensemble-1", "dynamic-ensemble-window-v4", latest),
                snapshot(PredictionModelType.DECAYED_FREQUENCY, "decayed-1", "historical-position-gaussian-v2", latest),
                snapshot(PredictionModelType.FREQUENCY_SIMILARITY,
                        "frequency-1", "frequency-similarity-v0", latest))));
        ModelTrainingCoordinator coordinator = new ModelTrainingCoordinator(
                drawStore, modelStore, new ObjectMapper(), taskExecutor,
                mock(NextDrawModelTrainer.class), mock(VectorTransitionModelTrainer.class),
                mock(DynamicEnsembleTrainer.class), mock(DecayedFrequencyModelTrainer.class),
                mock(FrequencySimilarityTrainer.class));

        assertThat(coordinator.requestIfStale()).isEqualTo(TrainingRunStatus.STARTED);
        verify(taskExecutor).execute(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "vector-transition-gaussian-v2", "vector-transition-gaussian-v3", "stale"})
    void failedIndependentAcceptanceNeverActivatesEvenOnInstallUpgradeOrStaleWaterline(String activeVectorAlgorithm) {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        MultiWindowModelOptimizer optimizer = mock(MultiWindowModelOptimizer.class);
        List<Draw> draws = draws(100);
        when(drawStore.allChronological()).thenReturn(draws);
        when(modelStore.activeBatch()).thenReturn(activeVectorAlgorithm.equals("none")
                ? Optional.empty() : Optional.of(activeBatch(
                        activeVectorAlgorithm.equals("stale") ? draws.get(98) : draws.getLast(),
                        activeVectorAlgorithm.equals("stale") ? "vector-transition-gaussian-v3" : activeVectorAlgorithm)));
        when(optimizer.optimize(draws)).thenReturn(optimization(draws, false));
        when(modelStore.activateBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelStore.saveInactive(any())).thenAnswer(invocation ->
                ((ModelSnapshot) invocation.getArgument(0)).withStatus(PredictionModelStatus.INACTIVE));
        ModelTrainingCoordinator coordinator = new ModelTrainingCoordinator(
                drawStore, modelStore, new ObjectMapper(), mock(TaskExecutor.class),
                optimizer);

        TrainingRunResult result = coordinator.retrainNow();

        assertThat(result.status().name()).isEqualTo(activeVectorAlgorithm.equals("none")
                ? "NO_ELIGIBLE_MODEL" : "RETAINED_ACTIVE_MODEL");
        assertThat(result.models()).hasSize(5);
        assertThat(result.models()).extracting(ModelTrainingResult::trainedThroughDrawId).containsOnly(80L);
        assertThat(result.optimization().rejectionReasons()).isNotEmpty();
        verify(modelStore, never()).activateBatch(anyList());
    }

    @Test
    void acceptedOptimizationActivatesOneConsistentBatchWithHoldoutMetricsAndReboundReferences() throws Exception {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        MultiWindowModelOptimizer optimizer = mock(MultiWindowModelOptimizer.class);
        List<Draw> draws = draws(100);
        when(drawStore.allChronological()).thenReturn(draws);
        when(modelStore.activeBatch()).thenReturn(Optional.empty());
        when(optimizer.optimize(draws)).thenReturn(optimization(draws, true));
        when(modelStore.activateBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ModelTrainingCoordinator coordinator = new ModelTrainingCoordinator(drawStore, modelStore,
                new ObjectMapper(), mock(TaskExecutor.class), optimizer);

        TrainingRunResult result = coordinator.retrainNow();

        assertThat(result.status()).isEqualTo(TrainingRunStatus.SUCCESS);
        assertThat(result.models()).hasSize(5).extracting(ModelTrainingResult::trainedThroughDrawId).containsOnly(100L);
        assertThat(result.models()).extracting(ModelTrainingResult::metrics).containsOnly(metrics());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelSnapshot>> captured = ArgumentCaptor.forClass(List.class);
        verify(modelStore).activateBatch(captured.capture());
        List<ModelSnapshot> batch = captured.getValue();
        assertThat(batch).extracting(ModelSnapshot::batchVersion).containsOnly(result.batchVersion());
        EnsembleArtifact ensemble = new ObjectMapper().readValue(batch.get(2).configJson(), EnsembleArtifact.class);
        assertThat(ensemble.logisticModelVersion()).isEqualTo(batch.get(0).modelVersion());
        assertThat(ensemble.vectorModelVersion()).isEqualTo(batch.get(1).modelVersion());
        assertThat(new ObjectMapper().readValue(batch.getFirst().configJson(), LogisticModelArtifact.class).trainingWindow())
                .isEqualTo(29);
        verify(modelStore, never()).saveInactive(any());
    }

    @Test
    void correctedHistoryDuringOptimizationCannotActivateAndQueuesOneFollowup() {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        MultiWindowModelOptimizer optimizer = mock(MultiWindowModelOptimizer.class);
        List<Draw> original = draws(100);
        List<Draw> corrected = new java.util.ArrayList<>(original);
        Draw old = original.getFirst();
        corrected.set(0, new Draw(old.id(), old.issueNo(), old.drawDate(),
                DltNumbers.of(List.of(30, 31, 32, 33, 34), List.of(11, 12)), old.sourceUrl()));
        java.util.concurrent.atomic.AtomicReference<List<Draw>> current = new java.util.concurrent.atomic.AtomicReference<>(original);
        when(drawStore.allChronological()).thenAnswer(invocation -> current.get());
        List<Runnable> queued = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<ModelTrainingCoordinator> coordinator = new java.util.concurrent.atomic.AtomicReference<>();
        when(optimizer.optimize(anyList())).thenAnswer(invocation -> {
            current.set(corrected);
            // Multiple import invalidations during training must be coalesced, not lost.
            coordinator.get().requestRetrain();
            coordinator.get().requestRetrain();
            return optimization(original, true);
        });
        coordinator.set(new ModelTrainingCoordinator(drawStore, modelStore, new ObjectMapper(), queued::add, optimizer));

        TrainingRunResult result = coordinator.get().retrainNow();

        assertThat(result.status().name()).isEqualTo("HISTORY_CHANGED");
        assertThat(queued).hasSize(1);
        verify(modelStore, never()).activateBatch(anyList());
        verify(modelStore, never()).saveInactive(any());
    }

    private static ModelOptimizationResult optimization(List<Draw> draws, boolean eligible) {
        List<Draw> fitted = draws.subList(0, eligible ? 100 : 80);
        GaussianZonePrediction front = new DecayedFrequencyCalculator().fit(fitted, NumberZone.FRONT);
        GaussianZonePrediction back = new DecayedFrequencyCalculator().fit(fitted, NumberZone.BACK);
        return new ModelOptimizationResult(
                new TrainedLogisticModel(new LogisticModelArtifact(3, 20, new double[7], new double[7], 29), validation(), metrics(), 423),
                new TrainedVectorTransitionModel(new VectorTransitionArtifact(3, new VectorTransitionParameters(30, 0.5, 5, 29),
                        new VectorTransitionParameters(30, 0.5, 5, 29), "dlt-zone-v1", fitted.getLast().id()), validation(), metrics(), 100),
                new TrainedEnsembleModel(new EnsembleArtifact(1, 0.5, 0.5, "l", "v"), validation(), metrics(), eligible),
                new TrainedDecayedFrequencyModel(new DecayedFrequencyArtifact(2, fitted.size(), front.distributions(), back.distributions(),
                        front.scores().values(), back.scores().values()), validation(), metrics(), 100),
                new TrainedFrequencySimilarityModel(new FrequencySimilarityArtifact(1, 0.5, 0.5, "n", "v"), validation(), metrics(), 100),
                eligible, new OptimizationSummary(List.of(0, 29), 29, 80, 24, 20, "I81", "I100", Map.of(),
                        eligible ? List.of() : List.of("未超过随机基线"), "有限候选搜索完成", 3, 1));
    }

    private static List<ModelSnapshot> activeBatch(Draw waterline, String vectorAlgorithm) {
        return List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, "logistic-1", "logistic-multi-window-v3", waterline),
                snapshot(PredictionModelType.VECTOR_TRANSITION, "vector-1", vectorAlgorithm, waterline),
                snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, "ensemble-1", "dynamic-ensemble-window-v4", waterline),
                snapshot(PredictionModelType.DECAYED_FREQUENCY, "decayed-1", "historical-position-gaussian-v2", waterline),
                snapshot(PredictionModelType.FREQUENCY_SIMILARITY,
                        "frequency-1", "frequency-similarity-window-v3", waterline));
    }

    private static ModelSnapshot snapshot(
            PredictionModelType type, String modelVersion, String algorithmVersion, Draw waterline) {
        String config = type == PredictionModelType.LOGISTIC_REGRESSION
                ? new ObjectMapper().valueToTree(new LogisticModelArtifact(3, 20, new double[7], new double[7], 0,
                        TrainingHistoryFingerprint.calculate(draws(100)))).toString() : "{}";
        return new ModelSnapshot(
                type, modelVersion, algorithmVersion, "batch-1", PredictionModelStatus.ACTIVE,
                config, 0, 100, 0, waterline.id(), waterline.issueNo(), waterline.drawDate(),
                Instant.EPOCH, metrics());
    }

    private static RollingPredictionSet validation() {
        return new RollingPredictionSet(List.of(
                new RollingPredictionSet.PeriodPrediction(99, scores(35), scores(12))));
    }

    private static CandidateScores scores(int count) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        IntStream.rangeClosed(1, count).forEach(number -> values.put(number, 0.2));
        return new CandidateScores(values);
    }

    private static ModelMetrics metrics() {
        return new ModelMetrics(
                0.4, 0.5, 0.8, 0.4,
                0.41, 0.51, 25.0 / 35, 4.0 / 12,
                true, true);
    }

    private static List<Draw> draws(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Draw(
                        index + 1L,
                        "I" + (index + 1),
                        LocalDate.of(2025, 1, 1).plusDays(index),
                        DltNumbers.of(
                                IntStream.range(0, 5)
                                        .map(offset -> ((index * 5 + offset) % 35) + 1).boxed().toList(),
                                IntStream.range(0, 2)
                                        .map(offset -> ((index * 2 + offset) % 12) + 1).boxed().toList()),
                        "test"))
                .toList();
    }
}
