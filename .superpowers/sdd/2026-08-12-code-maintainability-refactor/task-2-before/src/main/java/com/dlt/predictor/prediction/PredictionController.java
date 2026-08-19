package com.dlt.predictor.prediction;

import com.dlt.predictor.api.ApiResponse;
import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.dto.PredictionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dlt/predictions")
public class PredictionController {

    private static final String CUSTOM_NUMBERS_INPUT = "CUSTOM_NUMBERS";

    private final DrawStore drawStore;
    private final RecommendationEngine recommendationEngine;

    PredictionController(DrawStore drawStore) {
        this.drawStore = drawStore;
        this.recommendationEngine = new RecommendationEngine();
    }

    @PostMapping
    public ApiResponse<PredictionResponse> create(@Valid @RequestBody PredictionRequest request) {
        DltNumbers input = resolveInput(request);
        List<SimilarDraw> similarDraws = findSimilarDraws(input, request.topK());
        RecommendationResult result = recommendationEngine.recommend(similarDraws, LocalDate.now());

        return ApiResponse.ok(PredictionResponse.from(result));
    }

    private DltNumbers resolveInput(PredictionRequest request) {
        if (CUSTOM_NUMBERS_INPUT.equals(request.inputType())) {
            return DltNumbers.of(request.frontNumbers(), request.backNumbers());
        }

        return drawStore.all().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有开奖数据"))
                .numbers();
    }

    private List<SimilarDraw> findSimilarDraws(DltNumbers input, int topK) {
        float[] inputVector = FeatureEncoder.encode(input);
        return drawStore.all().stream()
                .filter(draw -> !draw.numbers().equals(input))
                .map(draw -> toSimilarDraw(draw, inputVector))
                .sorted(Comparator.comparingDouble(SimilarDraw::similarity).reversed())
                .limit(Math.min(topK, 10))
                .toList();
    }

    private SimilarDraw toSimilarDraw(Draw draw, float[] inputVector) {
        double similarity = cosine(inputVector, FeatureEncoder.encode(draw.numbers()));
        return new SimilarDraw(draw.id(), draw.issueNo(), draw.drawDate(), draw.numbers(), similarity);
    }

    private double cosine(float[] left, float[] right) {
        double similarity = 0;
        for (int index = 0; index < left.length; index++) {
            similarity += left[index] * right[index];
        }
        return similarity;
    }
}

record PredictionRequest(
        @NotBlank String inputType,
        @Min(1) @Max(10) int topK,
        List<Integer> frontNumbers,
        List<Integer> backNumbers) {
}
