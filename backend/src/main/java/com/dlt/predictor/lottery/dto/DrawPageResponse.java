package com.dlt.predictor.lottery.dto;

import java.util.List;

/**
 *
 * 开奖列表的分页响应，total 表示筛选后的完整记录数。
 *
 */
public record DrawPageResponse(
        List<DrawResponse> items,
        int page,
        int size,
        long total) {
}
