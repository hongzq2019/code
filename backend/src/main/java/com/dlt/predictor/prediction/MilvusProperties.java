package com.dlt.predictor.prediction;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 * Milvus 主机、端口、collection 名称及同步批量参数。
 *
 */
@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(String host, int port, String collectionName, int syncBatchSize) {

    String endpoint() {
        // Java SDK 使用完整 HTTP URI，而非仅 host:port。
        return "http://" + host + ":" + port;
    }
}
