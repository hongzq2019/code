package com.dlt.predictor.prediction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 *
 * 预测请求：支持最近一期开奖或用户自选号码作为相似检索输入。
 *
 */
public record PredictionRequest(
        @NotBlank String inputType,
        @Min(1) @Max(10) int topK,
        List<Integer> frontNumbers,
        List<Integer> backNumbers) {
}
