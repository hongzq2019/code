package com.dlt.predictor.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 *
 * 应用启动时补偿此前未完成或失败的 Milvus 同步。
 *
 */
@Component
@Order(10)
class VectorSyncStartupRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorSyncStartupRunner.class);

    private final DrawVectorSyncService drawVectorSyncService;

    VectorSyncStartupRunner(DrawVectorSyncService drawVectorSyncService) {
        this.drawVectorSyncService = drawVectorSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        VectorSyncResult result = drawVectorSyncService.syncPendingAndFailed();
        if (result.failed() > 0) {
            LOGGER.warn("Milvus vector synchronization completed with failures: {}", result);
        } else {
            LOGGER.info("Milvus vector synchronization completed: {}", result);
        }
    }
}
