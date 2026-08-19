package com.dlt.predictor.lottery;

import java.time.LocalDate;

/**
 *
 * 领域层的不可变开奖记录，向控制器和预测模块隐藏 JPA 实现细节。
 *
 */
public record Draw(
        Long id,
        String issueNo,
        LocalDate drawDate,
        DltNumbers numbers,
        String sourceUrl) {
}
