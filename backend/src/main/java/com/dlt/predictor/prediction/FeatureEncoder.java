package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.DltNumbers;

/**
 *
 * 将大乐透号码编码为联合向量和前后区独立归一化向量。
 *
 */
public final class FeatureEncoder {

    private static final int FRONT_ZONE_SIZE = 35;
    private static final int BACK_ZONE_SIZE = 12;
    private static final int JOINT_VECTOR_DIMENSIONS = FRONT_ZONE_SIZE + BACK_ZONE_SIZE;
    private static final int FRONT_SELECTION_COUNT = 5;
    private static final int BACK_SELECTION_COUNT = 2;
    private static final float FRONT_VALUE = (float) (1 / Math.sqrt(FRONT_SELECTION_COUNT));
    private static final float BACK_VALUE = (float) (1 / Math.sqrt(BACK_SELECTION_COUNT));

    private FeatureEncoder() {
    }

    public static float[] encodeJoint(DltNumbers numbers) {
        float[] vector = new float[JOINT_VECTOR_DIMENSIONS];
        float[] front = encodeFront(numbers);
        float[] back = encodeBack(numbers);
        System.arraycopy(front, 0, vector, 0, front.length);
        System.arraycopy(back, 0, vector, FRONT_ZONE_SIZE, back.length);
        return vector;
    }

    public static float[] encodeFront(DltNumbers numbers) {
        float[] vector = new float[FRONT_ZONE_SIZE];
        numbers.front().forEach(number -> vector[number - 1] = FRONT_VALUE);
        return vector;
    }

    public static float[] encodeBack(DltNumbers numbers) {
        float[] vector = new float[BACK_ZONE_SIZE];
        numbers.back().forEach(number -> vector[number - 1] = BACK_VALUE);
        return vector;
    }

    /**
     *
     * 兼容旧调用；新代码应明确选择联合或分区编码。
     *
     */
    public static float[] encode(DltNumbers numbers) {
        return encodeJoint(numbers);
    }
}
