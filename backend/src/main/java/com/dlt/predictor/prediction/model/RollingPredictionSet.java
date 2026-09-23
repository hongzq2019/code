package com.dlt.predictor.prediction.model;

import java.util.List;

public record RollingPredictionSet(List<PeriodPrediction> periods) {
    public record PeriodPrediction(int targetIndex, CandidateScores front, CandidateScores back) {
    }
}
