package com.dlt.predictor.prediction.model;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

public record CandidateScores(Map<Integer, Double> values, List<Integer> selectedNumbers) {
    public CandidateScores {
        values = Collections.unmodifiableMap(new TreeMap<>(values));
        selectedNumbers = selectedNumbers == null ? List.of() : List.copyOf(selectedNumbers);
    }

    public CandidateScores(Map<Integer, Double> values) {
        this(values, List.of());
    }
}
