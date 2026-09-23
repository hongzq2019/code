package com.dlt.predictor.prediction.model;

/**
 *
 * 向量转移模型的可持久化版本化参数。
 *
 */
public record VectorTransitionArtifact(
        int artifactVersion,
        int neighborCount,
        String vectorVersion,
        long indexedThroughDrawId) {
}
