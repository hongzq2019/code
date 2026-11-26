package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.List;

/** 使用此前已验证的预测表现，为逻辑回归与向量转移分别选择前后区权重。 */
public class DynamicEnsembleTrainer {

    public TrainedEnsembleModel train(
            List<Draw> draws, RollingPredictionSet logistic, RollingPredictionSet vector,
            String logisticModelVersion, String vectorModelVersion) {
        CausalBlendTrainer.Result result = new CausalBlendTrainer().train(draws, logistic, vector, true);
        return new TrainedEnsembleModel(
                new EnsembleArtifact(1, result.frontWeight(), result.backWeight(),
                        logisticModelVersion, vectorModelVersion),
                result.validation(), result.metrics(),
                result.metrics().frontBeatsBaseline() && result.metrics().backBeatsBaseline());
    }

    public CandidateScores combine(CandidateScores logistic, CandidateScores vector, double logisticWeight) {
        return CausalBlendTrainer.combine(logistic, vector, logisticWeight);
    }
}
