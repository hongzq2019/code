package com.dlt.predictor.prediction.model;

public enum TrainingRunStatus {
    STARTED,
    SUCCESS,
    RETAINED_ACTIVE_MODEL,
    NO_ELIGIBLE_MODEL,
    HISTORY_CHANGED,
    INSUFFICIENT_DATA,
    TRAINING_IN_PROGRESS,
    FAILED
}
