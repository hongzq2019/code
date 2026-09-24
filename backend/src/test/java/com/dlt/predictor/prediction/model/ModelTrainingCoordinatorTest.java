package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

class ModelTrainingCoordinatorTest {

    @Test
    void activatesOneCompleteFourModelBatchAtLatestWaterline() {
        DrawStore drawStore = mock(DrawStore.class);
        PredictionModelStore modelStore = mock(PredictionModelStore.class);
        NextDrawModelTrainer logisticTrainer = mock(NextDrawModelTrainer.class);
        VectorTransitionModelTrainer vectorTrainer = mock(VectorTransitionModelTrainer.class);
        DynamicEnsembleTrainer ensembleTrainer = mock(DynamicEnsembleTrainer.class);
        DecayedFrequencyModelTrainer decayedTrainer = mock(DecayedFrequencyModelTrainer.class);
        List<Draw> draws = draws(100);
        RollingPredictionSet validation = validation();
        when(drawStore.allChronological()).thenReturn(draws);
        when(modelStore.activeBatch()).thenReturn(Optional.empty());
        when(logisticTrainer.train(anyList(), anyList())).thenReturn(new TrainedLogisticModel(
                new LogisticModelArtifact(1, 20, new double[7], new double[7]),
                validation, metrics(), 100));
        when(vectorTrainer.train(anyList(), anyList())).thenReturn(new TrainedVectorTransitionModel(
                new VectorTransitionArtifact(1, 30, "dlt-zone-v1", 100L),
                validation, metrics(), 100));
        when(ensembleTrainer.train(anyList(), any(), any(), anyString(), anyString()))
                .thenReturn(new TrainedEnsembleModel(
                        new EnsembleArtifact(1, 0.5, 0.5, "logistic-test", "vector-test"),
                        validation, metrics(), true));
        when(decayedTrainer.train(anyList(), anyList())).thenReturn(new TrainedDecayedFrequencyModel(
                new DecayedFrequencyArtifact(1, 200, scores(35).values(), scores(12).values()),
                validation, metrics(), 100));
        when(modelStore.activateBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ModelTrainingCoordinator coordinator = new ModelTrainingCoordinator(
                drawStore, modelStore, new ObjectMapper(), mock(TaskExecutor.class),
                logisticTrainer, vectorTrainer, ensembleTrainer, decayedTrainer);

        TrainingRunResult result = coordinator.retrainNow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(modelStore, times(1)).activateBatch(captor.capture());
        List<ModelSnapshot> candidates = captor.getValue();
        assertThat(result.status()).isEqualTo(TrainingRunStatus.SUCCESS);
        assertThat(candidates).extracting(ModelSnapshot::type).containsExactly(
                PredictionModelType.LOGISTIC_REGRESSION,
                PredictionModelType.VECTOR_TRANSITION,
                PredictionModelType.DYNAMIC_ENSEMBLE,
                PredictionModelType.DECAYED_FREQUENCY);
        assertThat(candidates).extracting(ModelSnapshot::batchVersion)
                .containsOnly(candidates.getFirst().batchVersion());
        assertThat(candidates).extracting(ModelSnapshot::trainedThroughDrawId).containsOnly(100L);
        assertThat(result.models()).hasSize(4);
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
