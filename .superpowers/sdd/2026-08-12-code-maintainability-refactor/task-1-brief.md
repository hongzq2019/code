# Task 1: Lock backend HTTP mapping contracts

## Goal

Replace direct serialisation of lottery and prediction domain records with explicit response DTOs while preserving the exact HTTP payload field names, request paths, query parameters and recommendation behavior.

## Files

- Create `backend/src/test/java/com/dlt/predictor/lottery/dto/DrawResponseTest.java`.
- Create `backend/src/test/java/com/dlt/predictor/prediction/dto/PredictionResponseTest.java`.
- Create `backend/src/main/java/com/dlt/predictor/lottery/dto/DrawResponse.java`.
- Create `backend/src/main/java/com/dlt/predictor/lottery/dto/DrawPageResponse.java`.
- Create `backend/src/main/java/com/dlt/predictor/prediction/dto/PredictionResponse.java`.
- Modify `backend/src/main/java/com/dlt/predictor/lottery/LotteryController.java`.
- Modify `backend/src/main/java/com/dlt/predictor/prediction/PredictionController.java`.

## Interfaces to produce

```java
public record DrawResponse(
        Long id,
        String issueNo,
        LocalDate drawDate,
        DltNumbers numbers,
        String sourceUrl) {
    public static DrawResponse from(Draw draw) { /* field-for-field mapping */ }
}

public record DrawPageResponse(List<DrawResponse> items, int page, int size, long total) { }

public record PredictionResponse(
        DltNumbers numbers,
        Map<Integer, Double> frontScores,
        Map<Integer, Double> backScores,
        List<SimilarDraw> similarDraws,
        String modelVersion,
        Instant generatedAt,
        String disclaimer) {
    public static PredictionResponse from(RecommendationResult result) { /* field-for-field mapping */ }
}
```

`Draw` is `Draw(Long id, String issueNo, LocalDate drawDate, DltNumbers numbers, String sourceUrl)`.

## Required tests and TDD evidence

1. First create and run `DrawResponseTest` that maps a draw with id `7`, issue `2025007`, date `2025-01-01`, numbers `[1,2,3,4,5]` / `[1,2]`, source `manual`; it must initially fail because `DrawResponse` does not exist.
2. First create and run `PredictionResponseTest` using `SimilarDraw(1L, "2025001", LocalDate.now().minusDays(8), DltNumbers.of(List.of(1,2,3,4,5), List.of(1,2)), 0.9)`; it must initially fail because `PredictionResponse` does not exist. After implementation assert `modelVersion == "mvp-1.0"` and exact disclaimer equality.
3. Implement DTO records and mappings, then run the two focused tests and `mvn test`.

## Controller constraints

- Keep public paths `/api/v1/dlt/draws`, `/api/v1/dlt/draws/{issueNo}`, `/api/v1/dlt/predictions`.
- Keep `page`, `size`, `issueNo`, `inputType`, `topK`, `frontNumbers`, `backNumbers` request names and current bounds.
- List response `data` must still contain `items`, `page`, `size`, `total` with the same values.
- Detail response `data` must keep current draw field names.
- Prediction response `data` must keep current result field names and exact values.
- Keep all number validation, 47-dimensional encoding, similarity and weighted recommendation logic unchanged.
- Format modified Java with conventional multi-line layout; do not add unrelated features.

## Verification

Run `mvn test -Dtest=DrawResponseTest,PredictionResponseTest` and then `mvn test`. Report exact commands and results.
