package com.dlt.predictor.lottery;

/**
 *
 * 入库结果及是否需要使对应 Milvus 向量失效的标志。
 *
 */
public record DrawUpsertResult(Draw draw, boolean vectorRefreshRequired) {
}
