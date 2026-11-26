package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.dto.ModelPredictionResponse;
import com.dlt.predictor.prediction.dto.PredictionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

class VectorTransitionPipelineTest {

    @Test
    void realFiveModelTrainingArtifactsCanBeReadAndUsedForPrediction() throws Exception {
        List<Draw> history = IntStream.range(0, 100).mapToObj(index -> new Draw(
                index + 1L, "I" + (index + 1), LocalDate.of(2026, 1, 1).plusDays(index),
                DltNumbers.of(
                        IntStream.range(0, 5).map(n -> (index * 5 + n) % 35 + 1).boxed().toList(),
                        IntStream.range(0, 2).map(n -> (index * 2 + n) % 12 + 1).boxed().toList()), "test"))
                .toList();
        DrawStore draws = mock(DrawStore.class);
        PredictionModelStore models = mock(PredictionModelStore.class);
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<List<ModelSnapshot>> active = new AtomicReference<>();
        when(draws.allChronological()).thenReturn(history);
        when(models.activeBatch()).thenAnswer(invocation -> Optional.ofNullable(active.get()));
        when(models.activateBatch(anyList())).thenAnswer(invocation -> {
            List<ModelSnapshot> snapshots = invocation.getArgument(0);
            active.set(snapshots);
            return snapshots;
        });

        // Isolate the artifact round trip from acceptance, which has dedicated real-policy tests.
        // All five model artifacts below are genuinely trained; only the acceptance boundary is controlled.
        List<RollingFold> folds = new RollingFoldBuilder().build(100, 20);
        TrainedLogisticModel logistic = new NextDrawModelTrainer().train(history, folds, 29);
        TrainedVectorTransitionModel vectorModel = new VectorTransitionModelTrainer().train(history, folds, 29);
        TrainedEnsembleModel ensemble = new DynamicEnsembleTrainer().train(history,
                logistic.validationPredictions(), vectorModel.validationPredictions(), "l", "v");
        TrainedDecayedFrequencyModel normal = new DecayedFrequencyModelTrainer().train(history, folds);
        TrainedFrequencySimilarityModel hybrid = new FrequencySimilarityTrainer().train(history,
                normal.validationPredictions(), vectorModel.validationPredictions(), "n", "v");
        MultiWindowModelOptimizer optimizer = mock(MultiWindowModelOptimizer.class);
        when(optimizer.optimize(history)).thenReturn(new ModelOptimizationResult(logistic, vectorModel, ensemble, normal,
                hybrid, true, new OptimizationSummary(List.of(0, 29), 29, 80, 24, 20,
                "I81", "I100", java.util.Map.of(), List.of(), "有限候选搜索完成", 0, 0)));
        TrainingRunResult trained = new ModelTrainingCoordinator(
                draws, models, mapper, new SyncTaskExecutor(), optimizer).retrainNow();
        assertThat(trained.status()).isEqualTo(TrainingRunStatus.SUCCESS);
        PredictionResponse response = new NextDrawPredictionService(
                draws, models, new VectorTransitionPredictor(), mapper).predict();

        assertThat(response.predictions()).hasSize(5);
        ModelPredictionResponse vector = response.predictions().get(1);
        assertThat(vector.type()).isEqualTo(PredictionModelType.VECTOR_TRANSITION);
        assertThat(vector.trainedThroughIssueNo()).isEqualTo("I100");
        assertThat(vector.frontScores()).hasSize(35);
        assertThat(vector.backScores()).hasSize(12);
        assertThat(vector.frontScores().values()).allMatch(p -> Double.isFinite(p) && p > 0 && p < 1);
        assertThat(vector.backScores().values()).allMatch(p -> Double.isFinite(p) && p > 0 && p < 1);
        assertThat(vector.frontScores().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(5.0, within(1.0e-12));
        assertThat(vector.backScores().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(2.0, within(1.0e-12));
        assertThat(response.predictions().get(4).type()).isEqualTo(PredictionModelType.FREQUENCY_SIMILARITY);

        List<Draw> corrected = new java.util.ArrayList<>(history);
        Draw old = history.getFirst();
        corrected.set(0, new Draw(old.id(), old.issueNo(), old.drawDate(),
                DltNumbers.of(List.of(30, 31, 32, 33, 34), List.of(11, 12)), old.sourceUrl()));
        when(draws.allChronological()).thenReturn(corrected);
        assertThatThrownBy(() -> new NextDrawPredictionService(
                draws, models, new VectorTransitionPredictor(), mapper).predict())
                .isInstanceOf(ModelUnavailableException.class).hasMessageContaining("历史开奖已发生变化");
    }
}
