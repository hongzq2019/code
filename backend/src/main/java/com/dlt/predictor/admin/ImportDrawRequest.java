package com.dlt.predictor.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 *
 * 管理端人工补录开奖数据时提交的表单；号码规则由 {@code DltNumbers} 二次校验。
 *
 */
public record ImportDrawRequest(
        @NotBlank String issueNo,
        @NotNull LocalDate drawDate,
        @NotNull List<Integer> frontNumbers,
        @NotNull List<Integer> backNumbers) {
}
