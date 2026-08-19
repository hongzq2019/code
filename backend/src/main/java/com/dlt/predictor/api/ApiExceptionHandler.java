package com.dlt.predictor.api;

import org.springframework.http.ResponseEntity;
import com.dlt.predictor.prediction.VectorStoreException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 *
 * 将可预期的领域异常转换为前端可消费的统一 API 响应。
 *
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, exception.getMessage()));
    }

    /**
     *
     * Milvus 是预测检索的必要依赖，故障时明确返回 503 而非退化为内存全表扫描。
     *
     */
    @ExceptionHandler(VectorStoreException.class)
    ResponseEntity<ApiResponse<Void>> handleVectorStore(VectorStoreException exception) {
        return ResponseEntity.status(503).body(ApiResponse.error(503, "Milvus vector search is unavailable"));
    }
}
