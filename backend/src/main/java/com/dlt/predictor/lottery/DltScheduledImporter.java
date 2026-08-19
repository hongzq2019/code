package com.dlt.predictor.lottery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 *
 * 根据配置的开奖日 cron 自动触发官方数据采集。
 *
 */
@Component
class DltScheduledImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DltScheduledImporter.class);

    private final DltCrawlerService dltCrawlerService;

    DltScheduledImporter(DltCrawlerService dltCrawlerService) {
        this.dltCrawlerService = dltCrawlerService;
    }

    /**
     *
     * cron 和时区均外置配置，部署环境可以按照官方开奖发布时间调整。
     *
     */
    @Scheduled(cron = "${lottery.dlt.sync-cron}", zone = "${lottery.dlt.time-zone}")
    void importRecentDraws() {
        DltCrawlerResult result = dltCrawlerService.importRecentDraws();
        if ("FAILED".equals(result.result())) {
            LOGGER.warn("Scheduled DLT import failed: {}", result.message());
        } else {
            LOGGER.info("Scheduled DLT import completed: {}", result);
        }
    }
}
