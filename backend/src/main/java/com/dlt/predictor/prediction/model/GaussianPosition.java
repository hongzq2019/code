package com.dlt.predictor.prediction.model;

/** 升序号码的某一位置在全部历史中的正态分布参数。 */
public record GaussianPosition(double mean, double standardDeviation) {
}
