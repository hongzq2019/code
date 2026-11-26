package com.dlt.predictor.prediction.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 *
 * 历史正态分布的逐位置参数与完整候选边际概率，保留既有模型类型。
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DecayedFrequencyArtifact(
        int artifactVersion,
        int historyDrawCount,
        List<GaussianPosition> frontDistributions,
        List<GaussianPosition> backDistributions,
        Map<Integer, Double> frontProbabilities,
        Map<Integer, Double> backProbabilities) {

    public DecayedFrequencyArtifact {
        frontDistributions = frontDistributions == null ? List.of() : List.copyOf(frontDistributions);
        backDistributions = backDistributions == null ? List.of() : List.copyOf(backDistributions);
        frontProbabilities = immutableSortedCopy(frontProbabilities);
        backProbabilities = immutableSortedCopy(backProbabilities);
    }

    private static Map<Integer, Double> immutableSortedCopy(Map<Integer, Double> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
