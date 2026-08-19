package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.DltNumbers;
import java.time.LocalDate;

/**
 *
 * Milvus 命中的历史开奖详情及其 IP 相似度分数。
 *
 */
public record SimilarDraw(
        Long drawId,
        String issueNo,
        LocalDate drawDate,
        DltNumbers numbers,
        double similarity) {
}
