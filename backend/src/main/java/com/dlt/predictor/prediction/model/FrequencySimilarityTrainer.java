package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.List;

/** 高频概率和向量相似度的融合，指标与其余模型采用同一逐期样本外口径。 */
public class FrequencySimilarityTrainer {

    public TrainedFrequencySimilarityModel train(
            List<Draw> draws, RollingPredictionSet decayedFrequency, RollingPredictionSet vector,
            String decayedFrequencyModelVersion, String vectorModelVersion) {
        CausalBlendTrainer.Result result = new CausalBlendTrainer().train(draws, decayedFrequency, vector, false);
        return new TrainedFrequencySimilarityModel(
                new FrequencySimilarityArtifact(1, result.frontWeight(), result.backWeight(),
                        decayedFrequencyModelVersion, vectorModelVersion),
                result.validation(), result.metrics(),
                result.validation().periods().size()
                        * (NumberZone.FRONT.candidateCount() + NumberZone.BACK.candidateCount()));
    }

    public CandidateScores combine(
            CandidateScores decayedFrequency, CandidateScores vector, double decayedFrequencyWeight) {
        return CausalBlendTrainer.combine(decayedFrequency, vector, decayedFrequencyWeight);
    }
}
