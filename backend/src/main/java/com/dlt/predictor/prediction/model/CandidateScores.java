package com.dlt.predictor.prediction.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record CandidateScores(Map<Integer, Double> values) {
    public CandidateScores {
        values = Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
