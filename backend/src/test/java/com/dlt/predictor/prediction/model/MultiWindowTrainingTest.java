package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MultiWindowTrainingTest {

    @Test
    void raw29PeriodWindowExcludesOlderLogisticLabelsAndFeatures() {
        List<Draw> first = draws(65);
        List<Draw> changed = new ArrayList<>(first);
        for (int index = 0; index < 36; index++) {
            changed.set(index, draw(index, List.of(30, 31, 32, 33, 34), List.of(11, 12)));
        }
        NextDrawModelTrainer trainer = new NextDrawModelTrainer();

        LogisticModelArtifact left = trainer.fitLatest(first, 29);
        LogisticModelArtifact right = trainer.fitLatest(changed, 29);

        assertThat(left.frontWeights()).containsExactly(right.frontWeights());
        assertThat(left.backWeights()).containsExactly(right.backWeights());
        assertThat(left.trainingWindow()).isEqualTo(29);
        assertThat(trainer.fitLatest(first, 0).frontWeights())
                .isNotEqualTo(trainer.fitLatest(changed, 0).frontWeights());
        changed.set(64, draw(64, List.of(30, 31, 32, 33, 34), List.of(11, 12)));
        assertThat(trainer.fitLatest(changed, 29).frontWeights()).isNotEqualTo(left.frontWeights());
    }

    @Test
    void vectorSourceAndItsSuccessorMustBothBeInsideTheWindow() {
        List<Draw> first = new ArrayList<>(draws(35));
        first.set(0, draw(0, List.of(1, 2, 3, 4, 5), List.of(1, 2)));
        first.set(1, draw(1, List.of(30, 31, 32, 33, 34), List.of(11, 12)));
        first.set(34, draw(34, List.of(1, 2, 3, 4, 5), List.of(1, 2)));
        List<Draw> changed = new ArrayList<>(first);
        changed.set(1, draw(1, List.of(20, 21, 22, 23, 24), List.of(9, 10)));
        VectorTransitionPredictor predictor = new VectorTransitionPredictor();
        VectorTransitionParameters local = new VectorTransitionParameters(120, 0.5, 5, 29);

        assertThat(predictor.predictLatest(first, NumberZone.FRONT, local))
                .isEqualTo(predictor.predictLatest(changed, NumberZone.FRONT, local));
        assertThat(predictor.predictLatest(first, NumberZone.FRONT, new VectorTransitionParameters(120, 0.5, 5)))
                .isNotEqualTo(predictor.predictLatest(changed, NumberZone.FRONT, new VectorTransitionParameters(120, 0.5, 5)));
        changed.set(31, draw(31, List.of(30, 31, 32, 33, 34), List.of(11, 12)));
        assertThat(predictor.predictLatest(first, NumberZone.FRONT, local))
                .isNotEqualTo(predictor.predictLatest(changed, NumberZone.FRONT, local));
    }

    @Test
    void rejectsWindowsWithoutEnoughFeatureHistory() {
        NextDrawModelTrainer trainer = new NextDrawModelTrainer();
        for (int window : List.of(-1, 1, 20, 70)) {
            assertThatThrownBy(() -> trainer.fitLatest(draws(65), window))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new VectorTransitionParameters(30, 0.5, 5, -1).isValid()).isFalse();
    }

    static List<Draw> draws(int count) {
        return IntStream.range(0, count).mapToObj(index -> draw(index,
                IntStream.range(0, 5).map(offset -> (index * 7 + offset) % 35 + 1).boxed().toList(),
                IntStream.range(0, 2).map(offset -> (index * 3 + offset) % 12 + 1).boxed().toList())).toList();
    }

    static Draw draw(int index, List<Integer> front, List<Integer> back) {
        return new Draw(index + 1L, "I" + index, LocalDate.of(2020, 1, 1).plusDays(index),
                DltNumbers.of(front, back), "test");
    }
}
