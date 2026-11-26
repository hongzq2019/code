package com.dlt.predictor.prediction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 *
 * 向量转移模型的可持久化版本化参数。
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VectorTransitionArtifact(
        int artifactVersion,
        VectorTransitionParameters frontParameters,
        VectorTransitionParameters backParameters,
        String vectorVersion,
        long indexedThroughDrawId) {
}
