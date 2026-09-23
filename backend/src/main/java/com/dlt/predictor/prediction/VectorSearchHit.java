package com.dlt.predictor.prediction;

/**
 *
 * 向量数据库返回的最小命中信息；详情需要再由 PostgreSQL 补全。
 *
 */
public record VectorSearchHit(long drawId, double similarity) {
}
