package com.dlt.predictor.admin;

/**
 *
 * 管理端采集任务响应，分别提供抓取数量与实际变更数量。
 *
 */
public record SyncResponse(
        String result,
        int fetchedCount,
        int changedCount,
        String message) {
}
