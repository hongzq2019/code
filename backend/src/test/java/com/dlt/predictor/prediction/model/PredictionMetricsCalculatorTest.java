package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PredictionMetricsCalculatorTest {

    @Test
    void topNumbersUseAscendingNumberAsTieBreaker() {
        Map<Integer, Double> scores = Map.of(1, 0.5, 2, 0.7, 3, 0.7, 4, 0.4);

        assertThat(PredictionMetricsCalculator.topNumbers(scores, 2)).containsExactly(2, 3);
    }
}
