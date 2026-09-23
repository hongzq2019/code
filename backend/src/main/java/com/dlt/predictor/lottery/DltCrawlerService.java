package com.dlt.predictor.lottery;

import com.dlt.predictor.prediction.DrawVectorSyncService;
import com.dlt.predictor.prediction.VectorSyncResult;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * 协调一次官方开奖采集：分页抓取、按期号入库、补偿 Milvus，并记录完整任务日志。
 *
 */
@Service
public class DltCrawlerService {

    private final SportteryDltClient sportteryDltClient;
    private final DrawStore drawStore;
    private final DrawVectorSyncService drawVectorSyncService;
    private final CrawlerJobLogRepository crawlerJobLogRepository;
    private final SportteryDltProperties properties;

    DltCrawlerService(
            SportteryDltClient sportteryDltClient,
            DrawStore drawStore,
            DrawVectorSyncService drawVectorSyncService,
            CrawlerJobLogRepository crawlerJobLogRepository,
            SportteryDltProperties properties) {
        this.sportteryDltClient = sportteryDltClient;
        this.drawStore = drawStore;
        this.drawVectorSyncService = drawVectorSyncService;
        this.crawlerJobLogRepository = crawlerJobLogRepository;
        this.properties = properties;
    }

    public DltCrawlerResult importRecentDraws() {
        return importRecentDraws(properties.pageCount());
    }

    public DltCrawlerResult importRecentDraws(int pageCount) {
        Instant startedAt = Instant.now();
        try {
            List<OfficialDraw> officialDraws = sportteryDltClient.fetchRecentDraws(pageCount);
            int changedCount = 0;
            for (OfficialDraw officialDraw : officialDraws) {
                if (drawStore.upsertOfficial(officialDraw).vectorRefreshRequired()) {
                    changedCount++;
                }
            }
            VectorSyncResult vectorSyncResult = drawVectorSyncService.syncPendingAndFailed();
            String result = vectorSyncResult.failed() == 0 ? "SUCCESS" : "PARTIAL";
            String message = vectorSyncResult.failed() == 0
                    ? "已从中国体彩网同步开奖数据和向量索引"
                    : "开奖数据已保存，但有 " + vectorSyncResult.failed() + " 条向量同步失败";
            crawlerJobLogRepository.save(CrawlerJobLogEntity.completed(
                    startedAt, result, officialDraws.size(), changedCount, null));
            return new DltCrawlerResult(result, officialDraws.size(), changedCount, message);
        } catch (RuntimeException exception) {
            crawlerJobLogRepository.save(CrawlerJobLogEntity.completed(
                    startedAt, "FAILED", 0, 0, exception.getMessage()));
            return new DltCrawlerResult("FAILED", 0, 0, "从中国体彩网同步失败: " + exception.getMessage());
        }
    }
}
