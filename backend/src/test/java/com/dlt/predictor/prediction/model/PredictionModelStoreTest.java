package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionModelStoreTest {

    @Test
    void rejectsIncompleteActivationBeforeRepositoryChanges() {
        PredictionModelRepository repository = mock(PredictionModelRepository.class);
        PredictionModelStore store = new PredictionModelStore(repository);

        assertThatThrownBy(() -> store.activateBatch(List.of(
                snapshot(PredictionModelType.LOGISTIC_REGRESSION, "batch-a", 99L),
                snapshot(PredictionModelType.VECTOR_TRANSITION, "batch-a", 99L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("三种模型");

        verify(repository, never()).deactivateAllActive();
    }

    @Test
    void returnsEmptyWhenActiveModelsDoNotShareBatchAndWaterline() {
        PredictionModelRepository repository = mock(PredictionModelRepository.class);
        when(repository.findByStatusOrderByModelTypeAsc(PredictionModelStatus.ACTIVE)).thenReturn(List.of(
                PredictionModelEntity.from(snapshot(PredictionModelType.LOGISTIC_REGRESSION, "batch-a", 99L)),
                PredictionModelEntity.from(snapshot(PredictionModelType.VECTOR_TRANSITION, "batch-b", 99L)),
                PredictionModelEntity.from(snapshot(PredictionModelType.DYNAMIC_ENSEMBLE, "batch-a", 100L))));
        PredictionModelStore store = new PredictionModelStore(repository);

        assertThat(store.activeBatch()).isEmpty();
    }

    private static ModelSnapshot snapshot(PredictionModelType type, String batch, long waterline) {
        return new ModelSnapshot(
                type,
                type.name().toLowerCase() + "-1",
                "algorithm-v1",
                batch,
                PredictionModelStatus.INACTIVE,
                "{}",
                20,
                100,
                80,
                waterline,
                "2026099",
                LocalDate.of(2026, 9, 1),
                Instant.parse("2026-09-23T08:00:00Z"),
                new ModelMetrics(0.4, 0.5, 0.8, 0.4, 0.41, 0.51, 25.0 / 35, 4.0 / 12, true, true));
    }
}
