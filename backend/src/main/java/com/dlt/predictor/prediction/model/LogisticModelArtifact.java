package com.dlt.predictor.prediction.model;

public record LogisticModelArtifact(
        int artifactVersion,
        int featureWindow,
        double[] frontWeights,
        double[] backWeights,
        int trainingWindow,
        String trainingHistoryFingerprint) {

    public LogisticModelArtifact(int artifactVersion, int featureWindow, double[] frontWeights, double[] backWeights) {
        this(artifactVersion, featureWindow, frontWeights, backWeights, 0, null);
    }

    public LogisticModelArtifact(int artifactVersion, int featureWindow, double[] frontWeights, double[] backWeights,
            int trainingWindow) {
        this(artifactVersion, featureWindow, frontWeights, backWeights, trainingWindow, null);
    }
}
