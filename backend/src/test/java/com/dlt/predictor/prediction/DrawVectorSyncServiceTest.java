package com.dlt.predictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DrawVectorSyncServiceTest {

    @Test
    void marksDrawFailedWhenLogicalMultiVectorUpsertFails() {
        DrawStore drawStore = mock(DrawStore.class);
        VectorStore vectorStore = mock(VectorStore.class);
        Draw draw = new Draw(
                7L,
                "2026001",
                LocalDate.of(2026, 1, 3),
                DltNumbers.of(List.of(1, 6, 12, 20, 35), List.of(3, 11)),
                "official");
        when(drawStore.pendingOrFailedForVectorSync()).thenReturn(List.of(draw));
        doThrow(new VectorStoreException("transition failed", new IllegalStateException("offline")))
                .when(vectorStore)
                .upsert(eq(7L), any(float[].class), any(float[].class), any(float[].class));
        DrawVectorSyncService service = new DrawVectorSyncService(drawStore, vectorStore);

        VectorSyncResult result = service.syncPendingAndFailed();

        assertThat(result).isEqualTo(new VectorSyncResult(1, 0, 1));
        verify(vectorStore).upsert(eq(7L), any(float[].class), any(float[].class), any(float[].class));
        verify(drawStore, never()).markVectorReady(7L);
        verify(drawStore).markVectorFailed(eq(7L), contains("transition failed"));
    }
}
