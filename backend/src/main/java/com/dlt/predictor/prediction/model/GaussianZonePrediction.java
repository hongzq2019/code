package com.dlt.predictor.prediction.model;

import java.util.List;

/** 分区拟合参数、候选号码边际概率与联合评分最高的有序组合。 */
public record GaussianZonePrediction(List<GaussianPosition> distributions, CandidateScores scores) {
    public GaussianZonePrediction {
        distributions = List.copyOf(distributions);
    }
}
