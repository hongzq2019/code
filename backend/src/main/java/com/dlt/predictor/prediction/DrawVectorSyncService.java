package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import org.springframework.stereotype.Service;

/**
 *
 * 将 PostgreSQL 中待同步或失败的开奖记录编码后幂等写入 Milvus。
 *
 */
@Service
public class DrawVectorSyncService {

    private final DrawStore drawStore;
    private final VectorStore vectorStore;

    DrawVectorSyncService(DrawStore drawStore, VectorStore vectorStore) {
        this.drawStore = drawStore;
        this.vectorStore = vectorStore;
    }

    public VectorSyncResult syncPendingAndFailed() {
        int succeeded = 0;
        int failed = 0;
        java.util.List<Draw> pendingDraws = drawStore.pendingOrFailedForVectorSync();
        for (Draw draw : pendingDraws) {
            try {
                // write 成功才转换为 READY，防止关系库宣称已索引但 Milvus 实际未写入。
                vectorStore.upsert(draw.id(), FeatureEncoder.encode(draw.numbers()));
                drawStore.markVectorReady(draw.id());
                succeeded++;
            } catch (RuntimeException exception) {
                // 单条失败不阻断剩余记录，保留失败原因供下次补偿和人工重试。
                drawStore.markVectorFailed(draw.id(), exception.getMessage());
                failed++;
            }
        }
        return new VectorSyncResult(pendingDraws.size(), succeeded, failed);
    }
}
