package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.DltNumbers;

/**
 *
 * 将前区/后区号码编码为固定 47 维稀疏向量，供 Milvus 以内积计算相似度。
 *
 */
public final class FeatureEncoder {

    private static final int VECTOR_DIMENSIONS = 47;
    private static final int FRONT_ZONE_SIZE = 35;
    private static final int FRONT_SELECTION_COUNT = 5;
    private static final int BACK_SELECTION_COUNT = 2;
    private static final float FRONT_VALUE = (float) (1 / Math.sqrt(FRONT_SELECTION_COUNT));
    private static final float BACK_VALUE = (float) (1 / Math.sqrt(BACK_SELECTION_COUNT));

    private FeatureEncoder() {
    }

    public static float[] encode(DltNumbers numbers) {
        // 前后区分别归一化，使向量内积体现号码重合度而不是单纯偏向前区数量。
        float[] vector = new float[VECTOR_DIMENSIONS];
        numbers.front().forEach(number -> vector[number - 1] = FRONT_VALUE);
        numbers.back().forEach(number -> vector[FRONT_ZONE_SIZE + number - 1] = BACK_VALUE);
        return vector;
    }
}
