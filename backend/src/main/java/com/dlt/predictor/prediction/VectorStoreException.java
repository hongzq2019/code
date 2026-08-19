package com.dlt.predictor.prediction;

/**
 *
 * 包装 Milvus 的连接、建表、写入及检索异常，供 API 层统一映射为服务不可用。
 *
 */
public class VectorStoreException extends RuntimeException {

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
