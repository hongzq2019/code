package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LogisticRegressionTrainerTest {

    @ParameterizedTest
    @EnumSource(NumberZone.class)
    void uninformativeFeaturesRecoverActualInclusionProbability(NumberZone zone) {
        List<BinarySample> samples = IntStream.rangeClosed(1, zone.candidateCount())
                .mapToObj(number -> new BinarySample(number, new double[6],
                        number <= zone.selectionCount() ? 1 : 0)).toList();
        LogisticRegressionTrainer trainer = new LogisticRegressionTrainer();

        double probability = trainer.predict(trainer.train(samples, zone), new double[6]);

        assertThat(probability).isCloseTo(zone.selectionCount() / (double) zone.candidateCount(),
                org.assertj.core.data.Offset.offset(1.0e-6));
    }

    @Test
    void trainingIsDeterministicAndProducesProbabilities() {
        List<BinarySample> samples = List.of(
                new BinarySample(1, new double[]{1, 1, 1, 1, 0, 0}, 1),
                new BinarySample(2, new double[]{0, 0, 0, 0, 1, 0}, 0),
                new BinarySample(3, new double[]{1, 0.8, 0.7, 0.6, 0, 0.2}, 1),
                new BinarySample(4, new double[]{0, 0.1, 0.1, 0.1, 1, -0.1}, 0));
        LogisticRegressionTrainer trainer = new LogisticRegressionTrainer();

        double[] first = trainer.train(samples, NumberZone.FRONT);
        double[] second = trainer.train(samples, NumberZone.FRONT);

        assertThat(first).containsExactly(second);
        assertThat(first).hasSize(7);
        assertThat(trainer.predict(first, samples.getFirst().features())).isBetween(0.0, 1.0);
    }
}
