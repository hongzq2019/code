package com.dlt.predictor.lottery;

/**
 *
 * PostgreSQL 记录相对于 Milvus 索引的同步状态。
 *
 */
public enum VectorStatus {
    /**
     *
     * 号码刚创建或已修改，尚未写入 Milvus。
     *
     */
    PENDING,
    /**
     *
     * Milvus 已成功接收当前版本的号码向量。
     *
     */
    READY,
    /**
     *
     * 最近同步失败，后续启动、定时或人工操作会继续重试。
     *
     */
    FAILED
}
