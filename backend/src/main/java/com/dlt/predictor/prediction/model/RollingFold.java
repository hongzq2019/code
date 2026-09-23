package com.dlt.predictor.prediction.model;

public record RollingFold(int trainingUntil, int validationFrom, int validationUntil) {
}
