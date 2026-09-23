package com.dlt.predictor.prediction.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PredictionResponseTest {

    @Test
    void keepsBatchMetadataAndPredictionOrder() {
        Instant generatedAt = Instant.parse("2026-09-23T00:00:00Z");
        PredictionResponse response = new PredictionResponse(
                generatedAt, "2026100", "仅供参考", List.of());

        assertEquals(generatedAt, response.generatedAt());
        assertEquals("2026100", response.basedOnIssueNo());
        assertEquals("仅供参考", response.disclaimer());
        assertEquals(List.of(), response.predictions());
    }
}
