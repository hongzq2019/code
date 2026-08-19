package com.dlt.predictor.lottery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 * 中国体彩网大乐透采集端点和单次抓取期数的外部化配置。
 *
 */
@ConfigurationProperties(prefix = "lottery.dlt")
public record SportteryDltProperties(String sourceUrl, int pageSize) {
}
