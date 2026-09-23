package com.dlt.predictor.prediction.model;

public enum NumberZone {
    FRONT(35, 5),
    BACK(12, 2);

    private final int candidateCount;
    private final int selectionCount;

    NumberZone(int candidateCount, int selectionCount) {
        this.candidateCount = candidateCount;
        this.selectionCount = selectionCount;
    }

    public int candidateCount() { return candidateCount; }
    public int selectionCount() { return selectionCount; }
    public double positiveWeight() { return (candidateCount - selectionCount) / (double) selectionCount; }
}
