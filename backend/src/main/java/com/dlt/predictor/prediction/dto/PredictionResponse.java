package com.dlt.predictor.prediction.dto;

import java.time.Instant;
import java.util.List;

/**
 *
 * 三种已生效模型对下一期开奖的预测结果及训练水位。
 *
 */
public record PredictionResponse(
        Instant generatedAt,
        String basedOnIssueNo,
        String disclaimer,
        List<ModelPredictionResponse> predictions) {
}
