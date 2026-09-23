package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import org.springframework.stereotype.Service;

/**
 *
 * 将 PostgreSQL 中待同步或失败的开奖记录编码后幂等写入全部 Milvus 索引。
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
                vectorStore.upsert(
                        draw.id(),
                        FeatureEncoder.encodeJoint(draw.numbers()),
                        FeatureEncoder.encodeFront(draw.numbers()),
                        FeatureEncoder.encodeBack(draw.numbers()));
                drawStore.markVectorReady(draw.id());
                succeeded++;
            } catch (RuntimeException exception) {
                drawStore.markVectorFailed(draw.id(), exception.getMessage());
                failed++;
            }
        }
        return new VectorSyncResult(pendingDraws.size(), succeeded, failed);
    }
}
