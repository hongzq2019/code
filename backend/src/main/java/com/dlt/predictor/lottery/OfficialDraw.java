package com.dlt.predictor.lottery;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * 中国体彩网单条原始记录的标准化结果，同时保留原始载荷便于追溯。
 *
 */
record OfficialDraw(
        String issueNo,
        LocalDate drawDate,
        List<Integer> frontNumbers,
        List<Integer> backNumbers,
        String sourceUrl,
        String sourcePayload) {
}
