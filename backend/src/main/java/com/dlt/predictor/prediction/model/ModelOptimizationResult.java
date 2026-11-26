package com.dlt.predictor.prediction.model;

import java.util.Map;

public record ModelOptimizationResult(
        TrainedLogisticModel logistic,
        TrainedVectorTransitionModel vector,
        TrainedEnsembleModel ensemble,
        TrainedDecayedFrequencyModel normal,
        TrainedFrequencySimilarityModel hybrid,
        boolean eligible,
        OptimizationSummary summary) {

    public Map<PredictionModelType, ModelMetrics> metrics() {
        return Map.of(PredictionModelType.LOGISTIC_REGRESSION, logistic.metrics(),
                PredictionModelType.VECTOR_TRANSITION, vector.metrics(),
                PredictionModelType.DYNAMIC_ENSEMBLE, ensemble.metrics(),
                PredictionModelType.DECAYED_FREQUENCY, normal.metrics(),
                PredictionModelType.FREQUENCY_SIMILARITY, hybrid.metrics());
    }
}
