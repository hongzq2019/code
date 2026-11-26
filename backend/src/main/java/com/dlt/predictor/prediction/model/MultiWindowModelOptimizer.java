package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 有限窗口搜索只看开发段；尾部验收答案不回流到窗口、核参数或融合权重。 */
public class MultiWindowModelOptimizer {

    private final NextDrawModelTrainer logisticTrainer;
    private final VectorTransitionModelTrainer vectorTrainer;
    private final DynamicEnsembleTrainer ensembleTrainer;
    private final DecayedFrequencyModelTrainer normalTrainer;
    private final FrequencySimilarityTrainer hybridTrainer;
    private final VectorTransitionPredictor vectorPredictor = new VectorTransitionPredictor();
    private final DecayedFrequencyCalculator normalCalculator = new DecayedFrequencyCalculator();

    public MultiWindowModelOptimizer() {
        this(new NextDrawModelTrainer(), new VectorTransitionModelTrainer(), new DynamicEnsembleTrainer(),
                new DecayedFrequencyModelTrainer(), new FrequencySimilarityTrainer());
    }

    MultiWindowModelOptimizer(NextDrawModelTrainer logisticTrainer, VectorTransitionModelTrainer vectorTrainer,
            DynamicEnsembleTrainer ensembleTrainer, DecayedFrequencyModelTrainer normalTrainer,
            FrequencySimilarityTrainer hybridTrainer) {
        this.logisticTrainer = logisticTrainer;
        this.vectorTrainer = vectorTrainer;
        this.ensembleTrainer = ensembleTrainer;
        this.normalTrainer = normalTrainer;
        this.hybridTrainer = hybridTrainer;
    }

    public ModelOptimizationResult optimize(List<Draw> draws) {
        if (draws.size() < 100) {
            throw new ModelUnavailableException("至少需要 100 期开奖执行多窗口优化");
        }
        Comparator<Draw> chronology = Comparator.comparing(Draw::drawDate).thenComparing(Draw::id);
        for (int index = 1; index < draws.size(); index++) {
            if (chronology.compare(draws.get(index - 1), draws.get(index)) >= 0) {
                throw new IllegalArgumentException("优化数据必须严格按开奖时间和 ID 升序排列");
            }
        }
        int holdoutCount = Math.min(60, Math.max(20, draws.size() / 5));
        int developmentCount = draws.size() - holdoutCount;
        List<Draw> development = draws.subList(0, developmentCount);
        List<RollingFold> folds = new RollingFoldBuilder().build(developmentCount, TrainingDatasetBuilder.FEATURE_WINDOW);
        int firstTrainingCount = folds.getFirst().trainingUntil();
        TrainedDecayedFrequencyModel normal = normalTrainer.train(development, folds);
        List<Integer> evaluated = new ArrayList<>();
        Candidate control = null;
        Candidate selected = null;
        for (int window : List.of(0, 29, 60, 120)) {
            if (window > firstTrainingCount) {
                continue;
            }
            Candidate candidate = train(development, folds, normal, window);
            evaluated.add(window);
            if (window == 0) {
                control = candidate;
            }
            if (selected == null || better(candidate, selected)) {
                selected = candidate;
            }
        }
        // 所有选择在此结束；后面的验收结果不能改变 selected。
        Evaluation selectedEvaluation = evaluate(draws, developmentCount, selected);
        Evaluation controlEvaluation = selected == control ? selectedEvaluation : evaluate(draws, developmentCount, control);
        List<String> reasons = OptimizationAcceptance.rejectionReasons(
                selectedEvaluation.metrics(), controlEvaluation.metrics());
        boolean eligible = reasons.isEmpty();
        OptimizationSummary summary = new OptimizationSummary(evaluated, selected.window(), developmentCount,
                selected.logistic().validationPredictions().periods().size(), holdoutCount,
                draws.get(developmentCount).issueNo(), draws.getLast().issueNo(), controlEvaluation.metrics(), reasons,
                "有限候选搜索完成", overlap(selectedEvaluation, NumberZone.FRONT), overlap(selectedEvaluation, NumberZone.BACK));
        return result(draws, selected, selectedEvaluation, eligible, summary);
    }

    private Candidate train(List<Draw> development, List<RollingFold> folds,
            TrainedDecayedFrequencyModel normal, int window) {
        TrainedLogisticModel logistic = logisticTrainer.train(development, folds, window);
        TrainedVectorTransitionModel vector = vectorTrainer.train(development, folds, window);
        TrainedEnsembleModel ensemble = ensembleTrainer.train(development,
                logistic.validationPredictions(), vector.validationPredictions(), "logistic", "vector");
        TrainedFrequencySimilarityModel hybrid = hybridTrainer.train(development,
                normal.validationPredictions(), vector.validationPredictions(), "normal", "vector");
        return new Candidate(window, logistic, vector, ensemble, normal, hybrid);
    }

    private static boolean better(Candidate candidate, Candidate incumbent) {
        double improvement = normalizedHits(candidate) - normalizedHits(incumbent);
        if (Math.abs(improvement) > 1.0e-12) {
            return improvement > 0;
        }
        double lossDifference = losses(candidate) - losses(incumbent);
        if (Math.abs(lossDifference) > 1.0e-12) {
            return lossDifference < 0;
        }
        if (incumbent.window() == 0 || candidate.window() == 0) {
            return candidate.window() == 0;
        }
        return candidate.window() > incumbent.window();
    }

    private static double normalizedHits(Candidate candidate) {
        return candidate.metrics().values().stream()
                .mapToDouble(value -> value.frontMeanHits() / (25.0 / 35) + value.backMeanHits() / (4.0 / 12)).sum();
    }

    private static double losses(Candidate candidate) {
        return candidate.metrics().values().stream().mapToDouble(value -> value.frontLogLoss() + value.backLogLoss()).sum();
    }

    private Evaluation evaluate(List<Draw> draws, int from, Candidate candidate) {
        Map<PredictionModelType, List<RollingPredictionSet.PeriodPrediction>> periods = new EnumMap<>(PredictionModelType.class);
        for (PredictionModelType type : PredictionModelType.values()) {
            periods.put(type, new ArrayList<>());
        }
        for (int target = from; target < draws.size(); target++) {
            List<Draw> history = draws.subList(0, target);
            LogisticModelArtifact fitted = logisticTrainer.fitLatest(history, candidate.window());
            CandidateScores lf = logisticTrainer.scoreLatest(history, NumberZone.FRONT, fitted.frontWeights());
            CandidateScores lb = logisticTrainer.scoreLatest(history, NumberZone.BACK, fitted.backWeights());
            CandidateScores vf = vectorPredictor.predictLatest(history, NumberZone.FRONT, candidate.vector().artifact().frontParameters());
            CandidateScores vb = vectorPredictor.predictLatest(history, NumberZone.BACK, candidate.vector().artifact().backParameters());
            CandidateScores nf = normalCalculator.score(history, NumberZone.FRONT);
            CandidateScores nb = normalCalculator.score(history, NumberZone.BACK);
            add(periods, PredictionModelType.LOGISTIC_REGRESSION, target, lf, lb);
            add(periods, PredictionModelType.VECTOR_TRANSITION, target, vf, vb);
            add(periods, PredictionModelType.DECAYED_FREQUENCY, target, nf, nb);
            add(periods, PredictionModelType.DYNAMIC_ENSEMBLE, target,
                    ensembleTrainer.combine(lf, vf, candidate.ensemble().artifact().frontLogisticWeight()),
                    ensembleTrainer.combine(lb, vb, candidate.ensemble().artifact().backLogisticWeight()));
            add(periods, PredictionModelType.FREQUENCY_SIMILARITY, target,
                    hybridTrainer.combine(nf, vf, candidate.hybrid().artifact().frontDecayedFrequencyWeight()),
                    hybridTrainer.combine(nb, vb, candidate.hybrid().artifact().backDecayedFrequencyWeight()));
        }
        Map<PredictionModelType, RollingPredictionSet> predictions = new EnumMap<>(PredictionModelType.class);
        Map<PredictionModelType, ModelMetrics> metrics = new EnumMap<>(PredictionModelType.class);
        periods.forEach((type, values) -> {
            RollingPredictionSet set = new RollingPredictionSet(List.copyOf(values));
            predictions.put(type, set);
            metrics.put(type, PredictionMetricsCalculator.calculate(draws, set));
        });
        return new Evaluation(predictions, metrics);
    }

    private static void add(Map<PredictionModelType, List<RollingPredictionSet.PeriodPrediction>> periods,
            PredictionModelType type, int target, CandidateScores front, CandidateScores back) {
        periods.get(type).add(new RollingPredictionSet.PeriodPrediction(target, front, back));
    }

    private static double overlap(Evaluation evaluation, NumberZone zone) {
        List<RollingPredictionSet.PeriodPrediction> vectors = evaluation.predictions().get(PredictionModelType.VECTOR_TRANSITION).periods();
        List<RollingPredictionSet.PeriodPrediction> ensembles = evaluation.predictions().get(PredictionModelType.DYNAMIC_ENSEMBLE).periods();
        double total = 0;
        for (int index = 0; index < vectors.size(); index++) {
            CandidateScores vector = zone == NumberZone.FRONT ? vectors.get(index).front() : vectors.get(index).back();
            CandidateScores ensemble = zone == NumberZone.FRONT ? ensembles.get(index).front() : ensembles.get(index).back();
            List<Integer> top = PredictionMetricsCalculator.topNumbers(vector, zone.selectionCount());
            total += PredictionMetricsCalculator.topNumbers(ensemble, zone.selectionCount()).stream().filter(top::contains).count();
        }
        return total / vectors.size();
    }

    private ModelOptimizationResult result(List<Draw> draws, Candidate selected, Evaluation evaluation,
            boolean eligible, OptimizationSummary summary) {
        LogisticModelArtifact logistic = eligible ? logisticTrainer.fitLatest(draws, selected.window()) : selected.logistic().artifact();
        VectorTransitionArtifact vector = selected.vector().artifact();
        DecayedFrequencyArtifact normal = selected.normal().artifact();
        if (eligible) {
            vector = new VectorTransitionArtifact(3, vector.frontParameters(), vector.backParameters(),
                    vector.vectorVersion(), draws.getLast().id());
            GaussianZonePrediction front = normalCalculator.fit(draws, NumberZone.FRONT);
            GaussianZonePrediction back = normalCalculator.fit(draws, NumberZone.BACK);
            normal = new DecayedFrequencyArtifact(2, draws.size(), front.distributions(), back.distributions(),
                    front.scores().values(), back.scores().values());
        }
        int fittedCount = eligible ? draws.size() : summary.developmentDrawCount();
        int logisticSamples = (selected.window() == 0 ? fittedCount - 20 : selected.window() - 20) * 47;
        int evaluationSamples = summary.holdoutPeriodCount() * 47;
        return new ModelOptimizationResult(
                new TrainedLogisticModel(logistic, evaluation.predictions().get(PredictionModelType.LOGISTIC_REGRESSION),
                        evaluation.metrics().get(PredictionModelType.LOGISTIC_REGRESSION), logisticSamples),
                new TrainedVectorTransitionModel(vector, evaluation.predictions().get(PredictionModelType.VECTOR_TRANSITION),
                        evaluation.metrics().get(PredictionModelType.VECTOR_TRANSITION), selected.vector().sampleCount()),
                new TrainedEnsembleModel(selected.ensemble().artifact(), evaluation.predictions().get(PredictionModelType.DYNAMIC_ENSEMBLE),
                        evaluation.metrics().get(PredictionModelType.DYNAMIC_ENSEMBLE), eligible),
                new TrainedDecayedFrequencyModel(normal, evaluation.predictions().get(PredictionModelType.DECAYED_FREQUENCY),
                        evaluation.metrics().get(PredictionModelType.DECAYED_FREQUENCY), evaluationSamples),
                new TrainedFrequencySimilarityModel(selected.hybrid().artifact(), evaluation.predictions().get(PredictionModelType.FREQUENCY_SIMILARITY),
                        evaluation.metrics().get(PredictionModelType.FREQUENCY_SIMILARITY), evaluationSamples), eligible, summary);
    }

    private record Candidate(int window, TrainedLogisticModel logistic, TrainedVectorTransitionModel vector,
            TrainedEnsembleModel ensemble, TrainedDecayedFrequencyModel normal, TrainedFrequencySimilarityModel hybrid) {
        Map<PredictionModelType, ModelMetrics> metrics() {
            return Map.of(PredictionModelType.LOGISTIC_REGRESSION, logistic.metrics(), PredictionModelType.VECTOR_TRANSITION, vector.metrics(),
                    PredictionModelType.DYNAMIC_ENSEMBLE, ensemble.metrics(), PredictionModelType.DECAYED_FREQUENCY, normal.metrics(),
                    PredictionModelType.FREQUENCY_SIMILARITY, hybrid.metrics());
        }
    }

    private record Evaluation(Map<PredictionModelType, RollingPredictionSet> predictions,
            Map<PredictionModelType, ModelMetrics> metrics) { }
}
