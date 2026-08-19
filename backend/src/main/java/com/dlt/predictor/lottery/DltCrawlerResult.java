package com.dlt.predictor.lottery;

/**
 *
 * 单次大乐透官方采集的汇总结果。
 *
 */
public record DltCrawlerResult(String result, int fetchedCount, int insertedCount, String message) {
}
