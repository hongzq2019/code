package com.dlt.predictor.prediction.model;

public record LogisticModelArtifact(
        int artifactVersion,
        int featureWindow,
        double[] frontWeights,
        double[] backWeights) {
}
