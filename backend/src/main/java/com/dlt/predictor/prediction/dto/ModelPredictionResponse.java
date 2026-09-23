package com.dlt.predictor.prediction.dto;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.prediction.model.PredictionModelType;
import java.time.Instant;
import java.util.Map;

public record ModelPredictionResponse(
        PredictionModelType type,
        String name,
        DltNumbers numbers,
        Map<Integer, Double> frontScores,
        Map<Integer, Double> backScores,
        String modelVersion,
        Instant trainedAt,
        int drawCount,
        String trainedThroughIssueNo,
        ModelMetricsResponse metrics,
        EnsembleWeightsResponse ensembleWeights,
        boolean frontBeatsBaseline,
        boolean backBeatsBaseline) {
}
