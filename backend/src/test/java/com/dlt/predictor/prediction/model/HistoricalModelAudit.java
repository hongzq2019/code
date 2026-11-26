package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.DltNumbers;
import com.dlt.predictor.lottery.Draw;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 离线审计：前 80% 历史训练，最后 20% 只作评估。输入为只读导出的五列 TSV。 */
public final class HistoricalModelAudit {

    private HistoricalModelAudit() {
    }

    public static void main(String[] args) throws Exception {
        List<Draw> draws = Files.readAllLines(Path.of(args[0])).stream().map(line -> {
            String[] fields = line.split("\t");
            return new Draw(Long.parseLong(fields[0]), fields[1], LocalDate.parse(fields[2]),
                    DltNumbers.of(numbers(fields[3]), numbers(fields[4])), "audit");
        }).toList();
        int cutoff = draws.size() * 4 / 5;
        List<Draw> training = draws.subList(0, cutoff);
        List<RollingFold> folds = new RollingFoldBuilder().build(cutoff, TrainingDatasetBuilder.FEATURE_WINDOW);
        NextDrawModelTrainer logisticTrainer = new NextDrawModelTrainer();
        TrainedLogisticModel logistic = logisticTrainer.train(training, folds);
        TrainedVectorTransitionModel vector = new VectorTransitionModelTrainer().train(training, folds);
        TrainedDecayedFrequencyModel frequency = new DecayedFrequencyModelTrainer().train(training, folds);
        DynamicEnsembleTrainer ensembleTrainer = new DynamicEnsembleTrainer();
        TrainedEnsembleModel ensemble = ensembleTrainer.train(training,
                logistic.validationPredictions(), vector.validationPredictions(), "logistic", "vector");
        FrequencySimilarityTrainer hybridTrainer = new FrequencySimilarityTrainer();
        TrainedFrequencySimilarityModel hybrid = hybridTrainer.train(training,
                frequency.validationPredictions(), vector.validationPredictions(), "frequency", "vector");
        VectorTransitionPredictor vectorPredictor = new VectorTransitionPredictor();
        DecayedFrequencyCalculator frequencyCalculator = new DecayedFrequencyCalculator();
        Map<String, List<RollingPredictionSet.PeriodPrediction>> predictions = new LinkedHashMap<>();
        for (String model : List.of("logistic", "vector", "ensemble", "frequency", "hybrid")) {
            predictions.put(model, new ArrayList<>());
        }
        for (int target = cutoff; target < draws.size(); target++) {
            List<Draw> history = draws.subList(0, target);
            CandidateScores lf = logisticTrainer.scoreLatest(history, NumberZone.FRONT, logistic.artifact().frontWeights());
            CandidateScores lb = logisticTrainer.scoreLatest(history, NumberZone.BACK, logistic.artifact().backWeights());
            CandidateScores vf = vectorPredictor.predictLatest(history, NumberZone.FRONT, vector.artifact().frontParameters());
            CandidateScores vb = vectorPredictor.predictLatest(history, NumberZone.BACK, vector.artifact().backParameters());
            CandidateScores ff = frequencyCalculator.score(history, NumberZone.FRONT);
            CandidateScores fb = frequencyCalculator.score(history, NumberZone.BACK);
            predictions.get("logistic").add(new RollingPredictionSet.PeriodPrediction(target, lf, lb));
            predictions.get("vector").add(new RollingPredictionSet.PeriodPrediction(target, vf, vb));
            predictions.get("frequency").add(new RollingPredictionSet.PeriodPrediction(target, ff, fb));
            predictions.get("ensemble").add(new RollingPredictionSet.PeriodPrediction(target,
                    ensembleTrainer.combine(lf, vf, ensemble.artifact().frontLogisticWeight()),
                    ensembleTrainer.combine(lb, vb, ensemble.artifact().backLogisticWeight())));
            predictions.get("hybrid").add(new RollingPredictionSet.PeriodPrediction(target,
                    hybridTrainer.combine(ff, vf, hybrid.artifact().frontDecayedFrequencyWeight()),
                    hybridTrainer.combine(fb, vb, hybrid.artifact().backDecayedFrequencyWeight())));
        }
        System.out.printf("draws=%d training=%d holdout=%d first=%s last=%s%n",
                draws.size(), cutoff, draws.size() - cutoff, draws.get(cutoff).issueNo(), draws.getLast().issueNo());
        System.out.println("model,periods,frontLogLoss,backLogLoss,frontMeanHits,backMeanHits");
        predictions.forEach((model, periods) -> {
            for (int count : List.of(periods.size(), 60, 30)) {
                if (count > periods.size()) {
                    continue;
                }
                ModelMetrics metrics = PredictionMetricsCalculator.calculate(draws,
                        new RollingPredictionSet(periods.subList(periods.size() - count, periods.size())));
                System.out.printf(java.util.Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%.6f%n", model, count,
                        metrics.frontLogLoss(), metrics.backLogLoss(), metrics.frontMeanHits(), metrics.backMeanHits());
            }
        });
    }

    private static List<Integer> numbers(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }
}
