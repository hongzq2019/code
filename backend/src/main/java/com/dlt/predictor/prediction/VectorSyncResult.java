package com.dlt.predictor.prediction;

/**
 *
 * 一次向量补偿任务的处理统计。
 *
 */
public record VectorSyncResult(int attempted, int succeeded, int failed) {
}
