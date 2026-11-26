package com.dlt.predictor.prediction;

import com.dlt.predictor.api.ApiResponse;
import com.dlt.predictor.prediction.dto.PredictionRequest;
import com.dlt.predictor.prediction.dto.PredictionResponse;
import com.dlt.predictor.prediction.model.NextDrawPredictionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 返回同一生效批次的五种下一期开奖模型结果。
 *
 */
@RestController
@RequestMapping("/api/v1/dlt/predictions")
public class PredictionController {

    private final NextDrawPredictionService predictionService;

    PredictionController(NextDrawPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping
    public ApiResponse<PredictionResponse> create(@RequestBody(required = false) PredictionRequest ignored) {
        return ApiResponse.ok(predictionService.predict());
    }
}
