package com.dlt.predictor.api;

import java.util.UUID;

/**
 *
 * 所有 REST 接口的统一包裹格式；traceId 用于关联客户端反馈与服务端日志。
 *
 */
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        // 每次响应独立生成 traceId，即使当前尚未接入分布式链路追踪也可定位一次请求。
        return new ApiResponse<>(200, "OK", data, UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, UUID.randomUUID().toString());
    }
}
