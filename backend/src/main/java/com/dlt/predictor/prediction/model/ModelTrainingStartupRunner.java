package com.dlt.predictor.prediction.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 *
 * 向量补偿完成后，异步检查模型是否落后于 PostgreSQL 开奖水位。
 *
 */
@Component
@Order(20)
class ModelTrainingStartupRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelTrainingStartupRunner.class);
    private final ModelTrainingCoordinator coordinator;

    ModelTrainingStartupRunner(ModelTrainingCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            LOGGER.info("Model training startup check: {}", coordinator.requestIfStale());
        } catch (RuntimeException exception) {
            LOGGER.warn("Model training startup check failed; application startup continues", exception);
        }
    }
}
