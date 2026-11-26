package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DltNumbers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 独立离线入口：不启动 Spring，不访问数据库或激活模型。 */
public final class MultiWindowOptimizationAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("用法：MultiWindowOptimizationAudit /path/to/history.tsv");
        }
        long startedAt = System.nanoTime();
        List<Draw> draws = loadDraws(Path.of(args[0]));
        ModelOptimizationResult result = new MultiWindowModelOptimizer().optimize(draws);
        OptimizationSummary summary = result.summary();
        System.out.printf("draws=%d development=%d tuningTargets=%d holdout=%d from=%s through=%s%n",
                draws.size(), summary.developmentDrawCount(), summary.tuningPeriodCount(), summary.holdoutPeriodCount(),
                summary.holdoutFromIssueNo(), summary.holdoutThroughIssueNo());
        System.out.printf("evaluatedWindows=%s selectedWindow=%d eligible=%s stop=%s%n",
                summary.evaluatedWindows(), summary.selectedWindow(), result.eligible(), summary.stopReason());
        System.out.println("model,candidateFrontHits,candidateBackHits,controlFrontHits,controlBackHits,frontLogLoss,backLogLoss");
        for (PredictionModelType type : PredictionModelType.values()) {
            ModelMetrics candidate = result.metrics().get(type);
            ModelMetrics control = summary.controlMetrics().get(type);
            System.out.printf(Locale.ROOT, "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n", type,
                    candidate.frontMeanHits(), candidate.backMeanHits(), control.frontMeanHits(), control.backMeanHits(),
                    candidate.frontLogLoss(), candidate.backLogLoss());
        }
        System.out.printf(Locale.ROOT, "randomBaselineHits=%.6f/%.6f vectorEnsembleOverlap=%.6f/%.6f%n",
                25.0 / 35, 4.0 / 12, summary.frontVectorEnsembleOverlap(), summary.backVectorEnsembleOverlap());
        System.out.println("frozenFrontVector=" + result.vector().artifact().frontParameters());
        System.out.println("frozenBackVector=" + result.vector().artifact().backParameters());
        System.out.println("frozenEnsemble=" + result.ensemble().artifact());
        summary.rejectionReasons().forEach(reason -> System.out.println("rejected: " + reason));
        System.out.printf(Locale.ROOT, "durationSeconds=%.3f%n", (System.nanoTime() - startedAt) / 1.0e9);
    }

    public static List<Draw> loadDraws(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        if (lines.size() < 100) {
            throw new IllegalArgumentException("离线优化至少需要 100 条五列 TSV 开奖记录");
        }
        List<Draw> draws = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<String> issues = new HashSet<>();
        for (String line : lines) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 5) {
                throw new IllegalArgumentException("TSV 每行必须包含 ID、期号、日期、前区、后区五列");
            }
            long id = Long.parseLong(fields[0]);
            if (!ids.add(id) || fields[1].isBlank() || !issues.add(fields[1])) {
                throw new IllegalArgumentException("开奖 ID 和期号必须存在且唯一");
            }
            draws.add(new Draw(id, fields[1], LocalDate.parse(fields[2]),
                    DltNumbers.of(numbers(fields[3]), numbers(fields[4])), "offline-audit"));
        }
        return List.copyOf(draws);
    }

    private static List<Integer> numbers(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }
}
