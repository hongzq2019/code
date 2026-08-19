package com.dlt.predictor.lottery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 *
 * PostgreSQL 中开奖主表的实体映射。
 *
 * <p>向量本身不存入关系库，只有同步状态和诊断信息留在此表中。</p>
 *
 */
@Entity
@Table(name = "dlt_draw")
class DrawEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_no", nullable = false, unique = true)
    private String issueNo;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "front_numbers", nullable = false)
    private String frontNumbers;

    @Column(name = "back_numbers", nullable = false)
    private String backNumbers;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "source_payload")
    private String sourcePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "vector_status", nullable = false)
    private VectorStatus vectorStatus;

    @Column(name = "vector_error")
    private String vectorError;

    @Column(name = "vector_attempted_at")
    private Instant vectorAttemptedAt;

    protected DrawEntity() {
    }

    DrawEntity(String issueNo, LocalDate drawDate, DltNumbers numbers, String sourceUrl, String sourcePayload) {
        this.issueNo = issueNo;
        this.drawDate = drawDate;
        this.frontNumbers = numbers.frontText();
        this.backNumbers = numbers.backText();
        this.sourceUrl = sourceUrl;
        this.sourcePayload = sourcePayload;
        markVectorPending();
    }

    boolean update(LocalDate drawDate, DltNumbers numbers, String sourceUrl, String sourcePayload) {
        // 仅开奖业务字段变化才会使现有向量失效；来源载荷变化不影响号码特征。
        boolean vectorRefreshRequired = !Objects.equals(this.drawDate, drawDate)
                || !Objects.equals(this.frontNumbers, numbers.frontText())
                || !Objects.equals(this.backNumbers, numbers.backText());
        this.drawDate = drawDate;
        this.frontNumbers = numbers.frontText();
        this.backNumbers = numbers.backText();
        this.sourceUrl = sourceUrl;
        this.sourcePayload = sourcePayload;
        if (vectorRefreshRequired) {
            markVectorPending();
        }
        return vectorRefreshRequired;
    }

    void markVectorPending() {
        // 置为待处理时清除上一次失败诊断，避免历史错误误导运维判断。
        vectorStatus = VectorStatus.PENDING;
        vectorError = null;
        vectorAttemptedAt = null;
    }

    void markVectorReady() {
        vectorStatus = VectorStatus.READY;
        vectorError = null;
        vectorAttemptedAt = Instant.now();
    }

    void markVectorFailed(String error) {
        // 限制错误长度，避免异常堆栈或远端响应无限增长占满数据库字段。
        vectorStatus = VectorStatus.FAILED;
        vectorError = error == null ? "Milvus synchronization failed" : error.substring(0, Math.min(error.length(), 1000));
        vectorAttemptedAt = Instant.now();
    }

    Long id() {
        return id;
    }

    VectorStatus vectorStatus() {
        return vectorStatus;
    }

    Draw toDraw() {
        // 数据库使用便于展示的逗号文本，领域对象仍使用已排序的整数集合。
        return new Draw(id, issueNo, drawDate, DltNumbers.of(parse(frontNumbers), parse(backNumbers)), sourceUrl);
    }

    private static java.util.List<Integer> parse(String text) {
        return java.util.Arrays.stream(text.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }
}
