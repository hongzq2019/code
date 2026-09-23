package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RollingFoldBuilderTest {

    @Test
    void splitsExactlyOneHundredDrawsWithoutOverlap() {
        List<RollingFold> folds = new RollingFoldBuilder().build(100, 20);

        assertThat(folds).hasSize(5);
        assertThat(folds).extracting(fold -> fold.validationUntil() - fold.validationFrom())
                .containsExactly(7, 7, 6, 6, 6);
        assertThat(folds.getFirst().trainingUntil()).isEqualTo(68);
        assertThat(folds.getFirst().validationFrom()).isEqualTo(68);
        assertThat(folds.getLast().validationUntil()).isEqualTo(100);
        for (int index = 1; index < folds.size(); index++) {
            assertThat(folds.get(index).trainingUntil()).isEqualTo(folds.get(index).validationFrom());
            assertThat(folds.get(index - 1).validationUntil()).isEqualTo(folds.get(index).validationFrom());
        }
    }
}
