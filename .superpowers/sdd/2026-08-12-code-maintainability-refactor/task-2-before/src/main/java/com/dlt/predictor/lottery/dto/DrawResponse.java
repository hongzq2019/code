package com.dlt.predictor.lottery.dto;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;

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
