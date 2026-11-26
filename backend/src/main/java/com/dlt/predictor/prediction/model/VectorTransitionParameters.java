package com.dlt.predictor.prediction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 每个分区独立回测选择的邻居数、高斯核宽度与均匀先验强度。 */
public record VectorTransitionParameters(int neighborCount, double bandwidth, double priorStrength, int historyWindow) {

    public VectorTransitionParameters(int neighborCount, double bandwidth, double priorStrength) {
        this(neighborCount, bandwidth, priorStrength, 0);
    }

    @JsonIgnore
    public boolean isValid() {
        return neighborCount > 0 && Double.isFinite(bandwidth) && bandwidth > 0
                && Double.isFinite(priorStrength) && priorStrength >= 0
                && (historyWindow == 0 || historyWindow >= 2);
    }
}
