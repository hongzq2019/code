package com.dlt.predictor.lottery.dto;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawResponseTest {

    @Test
    void mapsDrawWithoutChangingJsonFieldValues() {
        Draw draw = new Draw(
                7L,
                "2025007",
                LocalDate.of(2025, 1, 1),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)),
                "manual");

        DrawResponse response = DrawResponse.from(draw);

        assertEquals(7L, response.id());
        assertEquals("2025007", response.issueNo());
        assertEquals(LocalDate.of(2025, 1, 1), response.drawDate());
        assertEquals(draw.numbers(), response.numbers());
        assertEquals("manual", response.sourceUrl());
    }
}
