package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DrawStore;
import com.dlt.predictor.prediction.FeatureEncoder;
import com.dlt.predictor.prediction.VectorSearchHit;
import com.dlt.predictor.prediction.VectorStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * 使用相似历史开奖的下一期号码，为候选号码计算转移分数。
 *
 */
@Service
public class VectorTransitionPredictor {

    private final DrawStore drawStore;
    private final VectorStore vectorStore;

    public VectorTransitionPredictor() {
        this.drawStore = null;
        this.vectorStore = null;
    }

    @Autowired
    public VectorTransitionPredictor(DrawStore drawStore, VectorStore vectorStore) {
        this.drawStore = drawStore;
        this.vectorStore = vectorStore;
    }

    public CandidateScores predict(List<Draw> draws, int targetIndex, NumberZone zone, int limit) {
        if (targetIndex <= 0 || targetIndex >= draws.size()) {
            throw new IllegalArgumentException("目标下标超出开奖记录范围");
        }
        Draw query = draws.get(targetIndex - 1);
        List<Transition> transitions = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex + 1 < targetIndex; sourceIndex++) {
            double similarity = similarity(query, draws.get(sourceIndex), zone);
            if (similarity > 0) {
                transitions.add(new Transition(
                        draws.get(sourceIndex).id(), similarity, draws.get(sourceIndex + 1)));
            }
        }
        transitions.sort(Comparator.comparingDouble(Transition::similarity).reversed()
                .thenComparingLong(Transition::sourceDrawId));
        return aggregate(transitions.stream().limit(limit).toList(), zone);
    }

    public CandidateScores predictLatest(List<Draw> history, NumberZone zone, int limit) {
        if (drawStore == null || vectorStore == null) {
            throw new IllegalStateException("生产向量检索依赖尚未注入");
        }
        if (history.size() < 2) {
            throw new ModelUnavailableException("至少需要两期开奖记录才能执行向量转移预测");
        }
        Draw latest = history.get(history.size() - 1);
        float[] query = encode(latest, zone);
        List<VectorSearchHit> hits = zone == NumberZone.FRONT
                ? vectorStore.searchFront(query, history.size())
                : vectorStore.searchBack(query, history.size());

        Map<Long, Integer> indices = new HashMap<>();
        for (int index = 0; index < history.size(); index++) {
            indices.put(history.get(index).id(), index);
        }
        List<Transition> transitions = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            Integer sourceIndex = indices.get(hit.drawId());
            if (sourceIndex == null || sourceIndex + 1 >= history.size()
                    || history.get(sourceIndex).id().equals(latest.id()) || hit.similarity() <= 0) {
                continue;
            }
            transitions.add(new Transition(hit.drawId(), hit.similarity(), history.get(sourceIndex + 1)));
            if (transitions.size() == limit) {
                break;
            }
        }
        return aggregate(transitions, zone);
    }

    private static CandidateScores aggregate(List<Transition> transitions, NumberZone zone) {
        double totalSimilarity = transitions.stream().mapToDouble(Transition::similarity).sum();
        if (totalSimilarity <= 0) {
            throw new ModelUnavailableException("没有可用的相似历史转移记录");
        }
        Map<Integer, Double> scores = new LinkedHashMap<>();
        for (int number = 1; number <= zone.candidateCount(); number++) {
            scores.put(number, 0.0);
        }
        for (Transition transition : transitions) {
            List<Integer> successorNumbers = zone == NumberZone.FRONT
                    ? transition.successor().numbers().front()
                    : transition.successor().numbers().back();
            double contribution = transition.similarity() / totalSimilarity;
            successorNumbers.forEach(number -> scores.merge(number, contribution, Double::sum));
        }
        return new CandidateScores(scores);
    }

    private static double similarity(Draw left, Draw right, NumberZone zone) {
        float[] leftVector = encode(left, zone);
        float[] rightVector = encode(right, zone);
        double dot = 0;
        for (int index = 0; index < leftVector.length; index++) {
            dot += leftVector[index] * rightVector[index];
        }
        return dot;
    }

    private static float[] encode(Draw draw, NumberZone zone) {
        return zone == NumberZone.FRONT
                ? FeatureEncoder.encodeFront(draw.numbers())
                : FeatureEncoder.encodeBack(draw.numbers());
    }

    private record Transition(long sourceDrawId, double similarity, Draw successor) {
    }
}
