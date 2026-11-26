package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 对完整历史做精确向量转移，回测和线上共用同一计算路径。
 * 归一化多热向量的余弦相似度等于重合号码数 / 分区选号数。
 * 按重合数分组可完整保留邻居边界同分记录，也避免参数搜索重复扫描历史。
 */
@Service
public class VectorTransitionPredictor {

    public static final double DEFAULT_BANDWIDTH = 0.5;

    // 保留旧的无平滑调用，供既有调用者及基线对比使用；v3 生产使用持久化分区参数。
    public CandidateScores predict(List<Draw> draws, int targetIndex, NumberZone zone, int limit) {
        return predict(draws, targetIndex, zone, limit, DEFAULT_BANDWIDTH);
    }

    public CandidateScores predict(
            List<Draw> draws, int targetIndex, NumberZone zone, int limit, double bandwidth) {
        return predict(draws, targetIndex, zone, new VectorTransitionParameters(limit, bandwidth, 0));
    }

    public CandidateScores predict(
            List<Draw> draws, int targetIndex, NumberZone zone, VectorTransitionParameters parameters) {
        if (targetIndex < 1 || targetIndex >= draws.size()) {
            throw new IllegalArgumentException("目标下标超出开奖记录范围");
        }
        return prepare(draws, targetIndex, zone, parameters.historyWindow()).score(parameters);
    }

    public List<CandidateScores> predictCandidates(
            List<Draw> draws, int targetIndex, NumberZone zone, List<VectorTransitionParameters> parameters) {
        if (targetIndex < 1 || targetIndex >= draws.size()) {
            throw new IllegalArgumentException("目标下标超出开奖记录范围");
        }
        Map<Integer, TransitionGroups> groups = new java.util.HashMap<>();
        return parameters.stream().map(parameter -> groups.computeIfAbsent(parameter.historyWindow(),
                window -> prepare(draws, targetIndex, zone, window)).score(parameter)).toList();
    }

    public CandidateScores predictLatest(List<Draw> history, NumberZone zone, int limit) {
        return predictLatest(history, zone, limit, DEFAULT_BANDWIDTH);
    }

    public CandidateScores predictLatest(List<Draw> history, NumberZone zone, int limit, double bandwidth) {
        return predictLatest(history, zone, new VectorTransitionParameters(limit, bandwidth, 0));
    }

    public CandidateScores predictLatest(
            List<Draw> history, NumberZone zone, VectorTransitionParameters parameters) {
        if (history.size() < 2) {
            throw new ModelUnavailableException("至少需要两期开奖记录才能执行向量转移预测");
        }
        return prepare(history, history.size(), zone, parameters.historyWindow()).score(parameters);
    }

    private static TransitionGroups prepare(List<Draw> draws, int historyUntil, NumberZone zone, int historyWindow) {
        long query = mask(numbers(draws.get(historyUntil - 1), zone));
        int[] counts = new int[zone.selectionCount() + 1];
        int[][] successorCounts = new int[zone.selectionCount() + 1][zone.candidateCount() + 1];
        // source 的后继必须已经开奖；不读取 target 及其后的任何号码。
        int historyFrom = historyWindow == 0 ? 0 : Math.max(0, historyUntil - historyWindow);
        for (int source = historyFrom; source + 1 < historyUntil; source++) {
            int overlap = Long.bitCount(query & mask(numbers(draws.get(source), zone)));
            if (overlap == 0) {
                continue;
            }
            counts[overlap]++;
            for (int number : numbers(draws.get(source + 1), zone)) {
                successorCounts[overlap][number]++;
            }
        }
        return new TransitionGroups(zone, counts, successorCounts);
    }

    private static List<Integer> numbers(Draw draw, NumberZone zone) {
        return zone == NumberZone.FRONT ? draw.numbers().front() : draw.numbers().back();
    }

    private static long mask(List<Integer> numbers) {
        long result = 0;
        for (int number : numbers) {
            result |= 1L << (number - 1);
        }
        return result;
    }

    static double gaussianWeight(double cosineSimilarity, double bandwidth) {
        if (!Double.isFinite(bandwidth) || bandwidth <= 0) {
            throw new IllegalArgumentException("高斯核标准差必须为正的有限数值");
        }
        if (!Double.isFinite(cosineSimilarity) || cosineSimilarity < 0 || cosineSimilarity > 1) {
            throw new IllegalArgumentException("余弦相似度必须位于 [0, 1]");
        }
        return Math.exp(-(1.0 - cosineSimilarity) / (bandwidth * bandwidth));
    }

    private record TransitionGroups(NumberZone zone, int[] counts, int[][] successorCounts) {
        CandidateScores score(VectorTransitionParameters parameters) {
            if (parameters == null || !parameters.isValid()) {
                throw new IllegalArgumentException("向量转移参数无效");
            }
            double[] weightedCounts = new double[zone.candidateCount() + 1];
            double totalWeight = 0;
            int neighbors = 0;
            for (int overlap = zone.selectionCount(); overlap > 0 && neighbors < parameters.neighborCount(); overlap--) {
                if (counts[overlap] == 0) {
                    continue;
                }
                double weight = gaussianWeight(overlap / (double) zone.selectionCount(), parameters.bandwidth());
                neighbors += counts[overlap];
                totalWeight += counts[overlap] * weight;
                for (int number = 1; number <= zone.candidateCount(); number++) {
                    weightedCounts[number] += successorCounts[overlap][number] * weight;
                }
            }
            double denominator = totalWeight + parameters.priorStrength();
            if (denominator <= 0) {
                throw new ModelUnavailableException("没有可用的相似历史转移记录");
            }
            double prior = parameters.priorStrength() * (zone.selectionCount() / (double) zone.candidateCount());
            Map<Integer, Double> scores = new LinkedHashMap<>();
            for (int number = 1; number <= zone.candidateCount(); number++) {
                scores.put(number, (weightedCounts[number] + prior) / denominator);
            }
            return new CandidateScores(scores);
        }
    }
}
