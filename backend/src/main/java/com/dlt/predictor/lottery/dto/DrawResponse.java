package com.dlt.predictor.lottery.dto;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;

/**
 *
 * 公开开奖详情响应，基于领域对象转换，避免暴露实体内部同步字段。
 *
 */
public record DrawResponse(
        Long id,
        String issueNo,
        LocalDate drawDate,
        DltNumbers numbers,
        String sourceUrl) {

    public static DrawResponse from(Draw draw) {
        return new DrawResponse(
                draw.id(),
                draw.issueNo(),
                draw.drawDate(),
                draw.numbers(),
                draw.sourceUrl());
    }
}
