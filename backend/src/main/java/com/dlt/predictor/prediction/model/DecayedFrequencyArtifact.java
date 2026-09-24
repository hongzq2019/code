package com.dlt.predictor.prediction.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * 历史衰减概率模型的版本化参数和完整候选概率。
 *
 */
public record DecayedFrequencyArtifact(
        int artifactVersion,
        int halfLifeDraws,
        Map<Integer, Double> frontProbabilities,
        Map<Integer, Double> backProbabilities) {

    public DecayedFrequencyArtifact {
        frontProbabilities = immutableSortedCopy(frontProbabilities);
        backProbabilities = immutableSortedCopy(backProbabilities);
    }

    private static Map<Integer, Double> immutableSortedCopy(Map<Integer, Double> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
