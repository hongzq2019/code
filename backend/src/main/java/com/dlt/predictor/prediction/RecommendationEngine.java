package com.dlt.predictor.prediction;

import com.dlt.predictor.lottery.DltNumbers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * 将相似开奖记录按相似度与时间衰减加权，汇总出前区和后区号码的参考排序。
 *
 * <p>推荐结果仅服务于历史数据分析，不表达中奖概率或确定性预测。</p>
 *
 */
public class RecommendationEngine {

    private static final int FRONT_SELECTION_COUNT = 5;
    private static final int BACK_SELECTION_COUNT = 2;
    private static final String MODEL_VERSION = "mvp-1.0";
    private static final String RISK_DISCLAIMER = "仅根据历史数据相似性生成，用于数据分析与娱乐，不构成中奖概率承诺。";

    public RecommendationResult recommend(List<SimilarDraw> draws, LocalDate referenceDate) {
        Map<Integer, Double> frontScores = scores(draws, referenceDate, true);
        Map<Integer, Double> backScores = scores(draws, referenceDate, false);
        DltNumbers numbers = DltNumbers.of(
                top(frontScores, FRONT_SELECTION_COUNT),
                top(backScores, BACK_SELECTION_COUNT));

        return new RecommendationResult(
                numbers,
                frontScores,
                backScores,
                List.copyOf(draws),
                MODEL_VERSION,
                Instant.now(),
                RISK_DISCLAIMER);
    }

    private Map<Integer, Double> scores(
            List<SimilarDraw> draws,
            LocalDate referenceDate,
            boolean frontZone) {
        Map<Integer, Double> scores = new TreeMap<>();
        for (SimilarDraw draw : draws) {
            // 越相似、越新的历史样本，对号码分数的影响越大。
            double weight = recencyAdjustedWeight(draw, referenceDate);
            List<Integer> numbers = frontZone ? draw.numbers().front() : draw.numbers().back();
            numbers.forEach(number -> scores.merge(number, weight, Double::sum));
        }
        return Collections.unmodifiableMap(scores);
    }

    private double recencyAdjustedWeight(SimilarDraw draw, LocalDate referenceDate) {
        // 以两年为衰减尺度，避免很久以前的数据与近期样本拥有相同权重。
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(draw.drawDate(), referenceDate));
        return draw.similarity() * Math.exp(-ageDays / 730.0);
    }

    private List<Integer> top(Map<Integer, Double> scores, int count) {
        // 分数相同时按号码升序保证返回结果稳定、可复现。
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(count)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
