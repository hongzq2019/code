package com.dlt.predictor.prediction.model;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 *
 * 为模型训练提供容量受控的单线程执行器，避免并发训练争抢资源。
 *
 */
@Configuration
public class ModelTrainingConfiguration {

    @Bean(name = "modelTrainingExecutor")
    ThreadPoolTaskExecutor modelTrainingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("dlt-model-training-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
