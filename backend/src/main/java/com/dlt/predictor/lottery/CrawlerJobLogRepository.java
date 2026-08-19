package com.dlt.predictor.lottery;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * 采集任务日志的持久化仓库。
 *
 */
interface CrawlerJobLogRepository extends JpaRepository<CrawlerJobLogEntity, Long> {
}
