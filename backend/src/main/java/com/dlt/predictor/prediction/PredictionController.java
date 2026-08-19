package com.dlt.predictor.prediction;

import com.dlt.predictor.api.ApiResponse;
import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.dto.PredictionRequest;
import com.dlt.predictor.prediction.dto.PredictionResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 将输入号码映射为 Milvus 相似样本，并基于这些样本生成仅供参考的号码推荐。
 *
 */
@RestController
@RequestMapping("/api/v1/dlt/predictions")
public class PredictionController {

    private static final String CUSTOM_NUMBERS_INPUT = "CUSTOM_NUMBERS";

    private final DrawStore drawStore;
    private final RecommendationEngine recommendationEngine;
    private final SimilarDrawSearchService similarDrawSearchService;

    PredictionController(DrawStore drawStore, SimilarDrawSearchService similarDrawSearchService) {
        this.drawStore = drawStore;
        this.recommendationEngine = new RecommendationEngine();
        this.similarDrawSearchService = similarDrawSearchService;
    }

    @PostMapping
    public ApiResponse<PredictionResponse> create(@Valid @RequestBody PredictionRequest request) {
        DltNumbers input = resolveInput(request);
        List<SimilarDraw> similarDraws = findSimilarDraws(input, request.topK());
        RecommendationResult result = recommendationEngine.recommend(similarDraws, LocalDate.now());

        return ApiResponse.ok(PredictionResponse.from(result));
    }

    private DltNumbers resolveInput(PredictionRequest request) {
        // 自选号码直接校验；其他输入类型使用最新一期开奖作为特征输入。
        if (CUSTOM_NUMBERS_INPUT.equals(request.inputType())) {
            return DltNumbers.of(request.frontNumbers(), request.backNumbers());
        }

        return drawStore.all().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有开奖数据"))
                .numbers();
    }

    private List<SimilarDraw> findSimilarDraws(DltNumbers input, int topK) {
        // 相似度计算由 Milvus 完成，控制器不再对全部开奖记录做内存排序。
        return similarDrawSearchService.findSimilarDraws(input, topK);
    }
}
