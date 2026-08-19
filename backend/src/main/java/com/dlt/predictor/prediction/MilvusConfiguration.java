package com.dlt.predictor.prediction;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * 创建与配置化 Milvus 端点对应的 SDK 客户端。
 *
 */
@Configuration
@EnableConfigurationProperties(MilvusProperties.class)
class MilvusConfiguration {

    @Bean
    MilvusClientV2 milvusClient(MilvusProperties properties) {
        // SDK 客户端是线程安全的单例，所有索引读写共享连接池。
        return new MilvusClientV2(ConnectConfig.builder().uri(properties.endpoint()).build());
    }
}
