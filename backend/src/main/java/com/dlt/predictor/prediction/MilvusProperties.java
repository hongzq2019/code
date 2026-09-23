package com.dlt.predictor.prediction;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 * Milvus 主机、端口、联合/分区 collection 名称及同步批量参数。
 *
 */
@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(
        String host,
        int port,
        String collectionName,
        String transitionCollectionName,
        int syncBatchSize) {

    String endpoint() {
        return "http://" + host + ":" + port;
    }
}
