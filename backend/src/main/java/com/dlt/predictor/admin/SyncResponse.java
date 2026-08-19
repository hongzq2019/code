package com.dlt.predictor.admin;

/**
 *
 * 管理端采集任务的兼容响应，保留任务结果、发生变更的记录数和说明。
 *
 */
public record SyncResponse(
        String result,
        int insertedCount,
        String message) {
}
