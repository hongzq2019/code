package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrainingDatasetBuilderTest {

    @Test
    void targetNumbersChangeLabelsButNeverTargetFeatures() {
        List<Draw> first = draws(DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)));
        List<Draw> second = draws(DltNumbers.of(List.of(6, 7, 8, 9, 10), List.of(3, 4)));
        TrainingDatasetBuilder builder = new TrainingDatasetBuilder();

        BinarySample firstCandidate = builder.samples(first, NumberZone.FRONT, 20, 21).getFirst();
        BinarySample changedCandidate = builder.samples(second, NumberZone.FRONT, 20, 21).getFirst();

        assertThat(firstCandidate.features()).containsExactly(changedCandidate.features());
        assertThat(firstCandidate.label()).isOne();
        assertThat(changedCandidate.label()).isZero();
        assertThat(firstCandidate.features()).hasSize(6);
    }

    private static List<Draw> draws(DltNumbers target) {
        List<Draw> draws = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            draws.add(new Draw((long) index + 1, "H" + index, LocalDate.of(2026, 1, 1).plusDays(index),
                    DltNumbers.of(List.of(1, 6, 12, 20, 35), List.of(3, 11)), "test"));
        }
        draws.add(new Draw(21L, "TARGET", LocalDate.of(2026, 1, 21), target, "test"));
        return draws;
    }
}
