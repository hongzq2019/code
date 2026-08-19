package com.dlt.predictor.prediction;

import java.util.List;

/**
 *
 * 向量存储抽象，使同步和检索逻辑不直接依赖某个 Milvus SDK 调用。
 *
 */
interface VectorStore {

    /**
     *
     * 以开奖主键幂等写入或覆盖号码向量。
     *
     */
    void upsert(long drawId, float[] vector);

    /**
     *
     * 返回按照相似度排序的近邻命中。
     *
     */
    List<VectorSearchHit> search(float[] vector, int limit);
}
