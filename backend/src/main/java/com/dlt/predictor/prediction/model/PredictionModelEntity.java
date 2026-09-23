package com.dlt.predictor.prediction.model;

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

@Entity
@Table(name = "prediction_model")
class PredictionModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false)
    private PredictionModelType modelType;
    @Column(name = "model_version", nullable = false)
    private String modelVersion;
    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;
    @Column(name = "batch_version", nullable = false)
    private String batchVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PredictionModelStatus status;
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;
    @Column(name = "feature_window", nullable = false)
    private int featureWindow;
    @Column(name = "draw_count", nullable = false)
    private int drawCount;
    @Column(name = "sample_count", nullable = false)
    private int sampleCount;
    @Column(name = "trained_through_draw_id", nullable = false)
    private long trainedThroughDrawId;
    @Column(name = "trained_through_issue_no", nullable = false)
    private String trainedThroughIssueNo;
    @Column(name = "trained_through_date", nullable = false)
    private LocalDate trainedThroughDate;
    @Column(name = "trained_at", nullable = false)
    private Instant trainedAt;
    @Column(name = "front_log_loss", nullable = false)
    private double frontLogLoss;
    @Column(name = "back_log_loss", nullable = false)
    private double backLogLoss;
    @Column(name = "front_mean_hits", nullable = false)
    private double frontMeanHits;
    @Column(name = "back_mean_hits", nullable = false)
    private double backMeanHits;
    @Column(name = "front_baseline_log_loss", nullable = false)
    private double frontBaselineLogLoss;
    @Column(name = "back_baseline_log_loss", nullable = false)
    private double backBaselineLogLoss;
    @Column(name = "front_baseline_hits", nullable = false)
    private double frontBaselineHits;
    @Column(name = "back_baseline_hits", nullable = false)
    private double backBaselineHits;
    @Column(name = "front_beats_baseline", nullable = false)
    private boolean frontBeatsBaseline;
    @Column(name = "back_beats_baseline", nullable = false)
    private boolean backBeatsBaseline;

    protected PredictionModelEntity() {
    }

    static PredictionModelEntity from(ModelSnapshot snapshot) {
        PredictionModelEntity entity = new PredictionModelEntity();
        entity.modelType = snapshot.type();
        entity.modelVersion = snapshot.modelVersion();
        entity.algorithmVersion = snapshot.algorithmVersion();
        entity.batchVersion = snapshot.batchVersion();
        entity.status = snapshot.status();
        entity.configJson = snapshot.configJson();
        entity.featureWindow = snapshot.featureWindow();
        entity.drawCount = snapshot.drawCount();
        entity.sampleCount = snapshot.sampleCount();
        entity.trainedThroughDrawId = snapshot.trainedThroughDrawId();
        entity.trainedThroughIssueNo = snapshot.trainedThroughIssueNo();
        entity.trainedThroughDate = snapshot.trainedThroughDate();
        entity.trainedAt = snapshot.trainedAt();
        ModelMetrics metrics = snapshot.metrics();
        entity.frontLogLoss = metrics.frontLogLoss();
        entity.backLogLoss = metrics.backLogLoss();
        entity.frontMeanHits = metrics.frontMeanHits();
        entity.backMeanHits = metrics.backMeanHits();
        entity.frontBaselineLogLoss = metrics.frontBaselineLogLoss();
        entity.backBaselineLogLoss = metrics.backBaselineLogLoss();
        entity.frontBaselineHits = metrics.frontBaselineHits();
        entity.backBaselineHits = metrics.backBaselineHits();
        entity.frontBeatsBaseline = metrics.frontBeatsBaseline();
        entity.backBeatsBaseline = metrics.backBeatsBaseline();
        return entity;
    }

    void activate() {
        status = PredictionModelStatus.ACTIVE;
    }

    ModelSnapshot toSnapshot() {
        return new ModelSnapshot(modelType, modelVersion, algorithmVersion, batchVersion, status,
                configJson, featureWindow, drawCount, sampleCount, trainedThroughDrawId,
                trainedThroughIssueNo, trainedThroughDate, trainedAt,
                new ModelMetrics(frontLogLoss, backLogLoss, frontMeanHits, backMeanHits,
                        frontBaselineLogLoss, backBaselineLogLoss, frontBaselineHits,
                        backBaselineHits, frontBeatsBaseline, backBeatsBaseline));
    }
}
