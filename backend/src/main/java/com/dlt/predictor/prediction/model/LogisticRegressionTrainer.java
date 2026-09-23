package com.dlt.predictor.prediction.model;

import java.util.List;

public class LogisticRegressionTrainer {

    private static final int ITERATIONS = 800;
    private static final double LEARNING_RATE = 0.05;
    private static final double L2 = 0.001;
    private static final double EPSILON = 1.0e-12;

    public double[] train(List<BinarySample> samples, NumberZone zone) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("训练样本不能为空");
        }
        int featureCount = samples.getFirst().features().length;
        double[] weights = new double[featureCount + 1];
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            double[] gradient = new double[weights.length];
            for (BinarySample sample : samples) {
                double probability = predict(weights, sample.features());
                double classWeight = sample.label() == 1 ? zone.positiveWeight() : 1.0;
                double error = (probability - sample.label()) * classWeight;
                gradient[0] += error;
                for (int index = 0; index < featureCount; index++) {
                    gradient[index + 1] += error * sample.features()[index];
                }
            }
            weights[0] -= LEARNING_RATE * gradient[0] / samples.size();
            for (int index = 1; index < weights.length; index++) {
                weights[index] -= LEARNING_RATE * (gradient[index] / samples.size() + L2 * weights[index]);
            }
        }
        return weights;
    }

    public double predict(double[] weights, double[] features) {
        double value = weights[0];
        for (int index = 0; index < features.length; index++) {
            value += weights[index + 1] * features[index];
        }
        value = Math.max(-35, Math.min(35, value));
        double probability = 1.0 / (1.0 + Math.exp(-value));
        return Math.max(EPSILON, Math.min(1.0 - EPSILON, probability));
    }
}
