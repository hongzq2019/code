package com.dlt.predictor.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.dlt.predictor.lottery.DltNumbers;
import org.junit.jupiter.api.Test;

class FeatureEncoderTest {

    @Test
    void encodesNormalizedFrontAndBackPartitions() {
        DltNumbers numbers = DltNumbers.of(
                java.util.List.of(1, 6, 12, 20, 35),
                java.util.List.of(3, 11));

        float[] front = FeatureEncoder.encodeFront(numbers);
        float[] back = FeatureEncoder.encodeBack(numbers);

        assertThat(front).hasSize(35);
        assertThat(back).hasSize(12);
        assertThat(front[0]).isCloseTo((float) (1 / Math.sqrt(5)), within(1.0e-6f));
        assertThat(front[1]).isZero();
        assertThat(front[34]).isCloseTo((float) (1 / Math.sqrt(5)), within(1.0e-6f));
        assertThat(back[2]).isCloseTo((float) (1 / Math.sqrt(2)), within(1.0e-6f));
        assertThat(back[3]).isZero();
        assertThat(back[10]).isCloseTo((float) (1 / Math.sqrt(2)), within(1.0e-6f));
        assertThat(norm(front)).isCloseTo(1.0, within(1.0e-6));
        assertThat(norm(back)).isCloseTo(1.0, within(1.0e-6));
        assertThat(FeatureEncoder.encodeJoint(numbers)).hasSize(47);
    }

    private static double norm(float[] vector) {
        double squares = 0;
        for (float value : vector) {
            squares += value * value;
        }
        return Math.sqrt(squares);
    }
}
