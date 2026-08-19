package com.dlt.predictor;

import com.dlt.predictor.lottery.SportteryDltProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 *
 * 应用入口：启用组件扫描、定时采集以及中国体彩网采集参数绑定。
 *
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SportteryDltProperties.class)
public class DltPredictorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DltPredictorApplication.class, args);
    }
}
