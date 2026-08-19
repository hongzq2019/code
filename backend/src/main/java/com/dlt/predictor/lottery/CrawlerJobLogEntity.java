package com.dlt.predictor.lottery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 *
 * 每次官方开奖采集的运行审计记录，用于排查数据源和索引同步故障。
 *
 */
@Entity
@Table(name = "crawler_job_log")
class CrawlerJobLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private String result;

    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "error_message")
    private String errorMessage;

    protected CrawlerJobLogEntity() {
    }

    static CrawlerJobLogEntity completed(
            Instant startedAt, String result, int fetchedCount, int insertedCount, String errorMessage) {
        CrawlerJobLogEntity entity = new CrawlerJobLogEntity();
        entity.jobType = "DLT_SPORTTERY";
        entity.startedAt = startedAt;
        entity.finishedAt = Instant.now();
        entity.result = result;
        entity.fetchedCount = fetchedCount;
        entity.insertedCount = insertedCount;
        // 截断错误文本，防止第三方响应或堆栈信息占用不受控的存储空间。
        entity.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 2000));
        return entity;
    }
}
