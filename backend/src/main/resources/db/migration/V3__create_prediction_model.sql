CREATE TABLE prediction_model (
    id BIGSERIAL PRIMARY KEY,
    model_type VARCHAR(32) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    batch_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    config_json TEXT NOT NULL,
    feature_window INTEGER NOT NULL,
    draw_count INTEGER NOT NULL,
    sample_count INTEGER NOT NULL,
    trained_through_draw_id BIGINT NOT NULL REFERENCES dlt_draw(id),
    trained_through_issue_no VARCHAR(32) NOT NULL,
    trained_through_date DATE NOT NULL,
    trained_at TIMESTAMP WITH TIME ZONE NOT NULL,
    front_log_loss DOUBLE PRECISION NOT NULL,
    back_log_loss DOUBLE PRECISION NOT NULL,
    front_mean_hits DOUBLE PRECISION NOT NULL,
    back_mean_hits DOUBLE PRECISION NOT NULL,
    front_baseline_log_loss DOUBLE PRECISION NOT NULL,
    back_baseline_log_loss DOUBLE PRECISION NOT NULL,
    front_baseline_hits DOUBLE PRECISION NOT NULL,
    back_baseline_hits DOUBLE PRECISION NOT NULL,
    front_beats_baseline BOOLEAN NOT NULL,
    back_beats_baseline BOOLEAN NOT NULL,
    CONSTRAINT uq_prediction_model_version UNIQUE (model_type, model_version),
    CONSTRAINT ck_prediction_model_type CHECK (model_type IN ('LOGISTIC_REGRESSION', 'VECTOR_TRANSITION', 'DYNAMIC_ENSEMBLE')),
    CONSTRAINT ck_prediction_model_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_prediction_model_batch ON prediction_model(batch_version);
CREATE UNIQUE INDEX uq_prediction_model_active_type
    ON prediction_model(model_type)
    WHERE status = 'ACTIVE';
