package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.DltNumbers;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 *
 * 推荐引擎的内部输出，包含推荐号码、各号码分数和可解释的相似样本。
 *
 */
public record RecommendationResult(
        DltNumbers numbers,
        Map<Integer, Double> frontScores,
        Map<Integer, Double> backScores,
        List<SimilarDraw> similarDraws,
        String modelVersion,
        Instant generatedAt,
        String disclaimer) {
}
