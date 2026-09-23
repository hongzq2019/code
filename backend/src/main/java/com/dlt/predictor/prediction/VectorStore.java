package com.dlt.predictor.prediction;

import java.util.List;

/**
 *
 * 向量存储抽象，使同步和检索逻辑不直接依赖某个 Milvus SDK 调用。
 *
 */
public interface VectorStore {

    void upsert(long drawId, float[] jointVector, float[] frontVector, float[] backVector);

    List<VectorSearchHit> searchJoint(float[] vector, int limit);

    List<VectorSearchHit> searchFront(float[] vector, int limit);

    List<VectorSearchHit> searchBack(float[] vector, int limit);
}
