package com.dlt.predictor.prediction.dto;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.prediction.RecommendationEngine;
import com.dlt.predictor.prediction.RecommendationResult;
import com.dlt.predictor.prediction.SimilarDraw;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * 验证预测推荐结果可完整映射为对外响应。
 *
 */
class PredictionResponseTest {

    @Test
    void keepsRecommendationPayloadFields() {
        SimilarDraw draw = new SimilarDraw(
                1L,
                "2025001",
                LocalDate.now().minusDays(8),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                0.9);
        RecommendationResult result = new RecommendationEngine().recommend(List.of(draw), LocalDate.now());

        PredictionResponse response = PredictionResponse.from(result);

        assertEquals("mvp-1.0", response.modelVersion());
        assertEquals("仅根据历史数据相似性生成，用于数据分析与娱乐，不构成中奖概率承诺。", response.disclaimer());
        assertEquals(result.numbers(), response.numbers());
        assertEquals(result.frontScores(), response.frontScores());
        assertEquals(result.backScores(), response.backScores());
        assertEquals(result.similarDraws(), response.similarDraws());
        assertEquals(result.generatedAt(), response.generatedAt());
    }
}
