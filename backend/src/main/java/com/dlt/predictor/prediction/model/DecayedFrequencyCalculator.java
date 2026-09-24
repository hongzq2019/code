package com.dlt.predictor.prediction.model;

import com.dlt.predictor.lottery.Draw;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 *
 * 按期龄指数衰减统计前区或后区号码的历史出现概率。
 *
 */
public final class DecayedFrequencyCalculator {

    public CandidateScores score(List<Draw> history, NumberZone zone, int halfLifeDraws) {
        if (history == null || history.isEmpty()) {
            throw new ModelUnavailableException("没有可用于历史概率统计的开奖记录");
        }
        if (halfLifeDraws <= 0) {
            throw new IllegalArgumentException("半衰期必须大于 0");
        }

        Map<Integer, Double> weightedHits = new LinkedHashMap<>();
        IntStream.rangeClosed(1, zone.candidateCount())
                .forEach(number -> weightedHits.put(number, 0.0));
        double totalWeight = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            int age = history.size() - 1 - index;
            double drawWeight = weight(age, halfLifeDraws);
            totalWeight += drawWeight;
            List<Integer> numbers = zone == NumberZone.FRONT
                    ? history.get(index).numbers().front()
                    : history.get(index).numbers().back();
            numbers.forEach(number -> weightedHits.merge(number, drawWeight, Double::sum));
        }
        double divisor = totalWeight;
        weightedHits.replaceAll((number, hits) -> hits / divisor);
        return new CandidateScores(weightedHits);
    }

    double weight(int age, int halfLifeDraws) {
        return Math.pow(0.5, age / (double) halfLifeDraws);
    }
}
