package com.dlt.predictor.prediction.dto;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.prediction.RecommendationResult;
import com.dlt.predictor.prediction.SimilarDraw;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PredictionResponse(
        DltNumbers numbers,
        Map<Integer, Double> frontScores,
        Map<Integer, Double> backScores,
        List<SimilarDraw> similarDraws,
        String modelVersion,
        Instant generatedAt,
        String disclaimer) {

    public static PredictionResponse from(RecommendationResult result) {
        return new PredictionResponse(
                result.numbers(),
                result.frontScores(),
                result.backScores(),
                result.similarDraws(),
                result.modelVersion(),
                result.generatedAt(),
                result.disclaimer());
    }
}
