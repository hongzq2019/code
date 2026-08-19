package com.dlt.predictor.lottery;

import com.dlt.predictor.prediction.DrawVectorSyncService;
import com.dlt.predictor.prediction.VectorSyncResult;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * 协调一次官方开奖采集：抓取、按期号入库、补偿 Milvus，并记录完整任务日志。
 *
 */
@Service
public class DltCrawlerService {

    private final SportteryDltClient sportteryDltClient;
    private final DrawStore drawStore;
    private final DrawVectorSyncService drawVectorSyncService;
    private final CrawlerJobLogRepository crawlerJobLogRepository;

    DltCrawlerService(
            SportteryDltClient sportteryDltClient,
            DrawStore drawStore,
            DrawVectorSyncService drawVectorSyncService,
            CrawlerJobLogRepository crawlerJobLogRepository) {
        this.sportteryDltClient = sportteryDltClient;
        this.drawStore = drawStore;
        this.drawVectorSyncService = drawVectorSyncService;
        this.crawlerJobLogRepository = crawlerJobLogRepository;
    }

    public DltCrawlerResult importRecentDraws() {
        Instant startedAt = Instant.now();
        try {
            List<OfficialDraw> officialDraws = sportteryDltClient.fetchRecentDraws();
            int changedCount = 0;
            for (OfficialDraw officialDraw : officialDraws) {
                // 相同期号且号码未变化的记录不会重建向量，保证定时任务可安全重复执行。
                if (drawStore.upsertOfficial(officialDraw).vectorRefreshRequired()) {
                    changedCount++;
                }
            }
            // 将本次新增或修正过的开奖数据，以及此前失败的记录统一补偿到 Milvus。
            VectorSyncResult vectorSyncResult = drawVectorSyncService.syncPendingAndFailed();
            String result = vectorSyncResult.failed() == 0 ? "SUCCESS" : "PARTIAL";
            String message = vectorSyncResult.failed() == 0
                    ? "已从中国体彩网同步开奖数据和向量索引"
                    : "开奖数据已保存，但有 " + vectorSyncResult.failed() + " 条向量同步失败";
            crawlerJobLogRepository.save(CrawlerJobLogEntity.completed(
                    startedAt, result, officialDraws.size(), changedCount, null));
            return new DltCrawlerResult(result, officialDraws.size(), changedCount, message);
        } catch (RuntimeException exception) {
            // 采集异常只影响本次日志，绝不回滚或删除已有的历史开奖数据。
            crawlerJobLogRepository.save(CrawlerJobLogEntity.completed(
                    startedAt, "FAILED", 0, 0, exception.getMessage()));
            return new DltCrawlerResult("FAILED", 0, 0, "从中国体彩网同步失败: " + exception.getMessage());
        }
    }
}
