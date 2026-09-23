package com.dlt.predictor.prediction.model;

public record BinarySample(int candidateNumber, double[] features, int label) {
}
