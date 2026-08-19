package com.dlt.predictor.lottery.dto;

import java.util.List;

public record DrawPageResponse(
        List<DrawResponse> items,
        int page,
        int size,
        long total) {
}
