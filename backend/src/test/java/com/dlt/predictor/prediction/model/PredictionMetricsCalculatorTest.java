package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import com.dlt.predictor.lottery.Draw;
import com.dlt.predictor.lottery.DltNumbers;
import org.junit.jupiter.api.Test;

class PredictionMetricsCalculatorTest {

    @Test
    void evaluatesTheGeneratedOrderedCombinationInsteadOfReselectingMarginalTopNumbers() {
        ModelMetrics metrics = metrics(
                new CandidateScores(scores(35, true).values(), List.of(1, 2, 3, 4, 5)),
                new CandidateScores(scores(12, true).values(), List.of(1, 2)));

        assertThat(metrics.frontMeanHits()).isEqualTo(5);
        assertThat(metrics.backMeanHits()).isEqualTo(2);
    }

    @Test
    void hitFirstBaselineFlagDoesNotSilentlyRequireLowerProbabilityLoss() {
        ModelMetrics metrics = metrics(scores(35, false), scores(12, false));

        assertThat(metrics.frontMeanHits()).isEqualTo(5);
        assertThat(metrics.backMeanHits()).isEqualTo(2);
        assertThat(metrics.frontBeatsBaseline()).isTrue();
        assertThat(metrics.backBeatsBaseline()).isTrue();
    }

    @Test
    void lowerLogLossWithZeroHitsDoesNotCountAsBeatingBaseline() {
        ModelMetrics metrics = metrics(scores(35, true), scores(12, true));

        assertThat(metrics.frontLogLoss()).isLessThan(metrics.frontBaselineLogLoss());
        assertThat(metrics.backLogLoss()).isLessThan(metrics.backBaselineLogLoss());
        assertThat(metrics.frontMeanHits()).isZero();
        assertThat(metrics.backMeanHits()).isZero();
        assertThat(metrics.frontBeatsBaseline()).isFalse();
        assertThat(metrics.backBeatsBaseline()).isFalse();
    }

    private static ModelMetrics metrics(CandidateScores front, CandidateScores back) {
        Draw draw = new Draw(1L, "I1", LocalDate.of(2026, 1, 1),
                DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "test");
        return PredictionMetricsCalculator.calculate(List.of(draw), new RollingPredictionSet(List.of(
                new RollingPredictionSet.PeriodPrediction(0, front, back))));
    }

    private static CandidateScores scores(int count, boolean wrongTop) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        int selected = count == 35 ? 5 : 2;
        for (int number = 1; number <= count; number++) {
            values.put(number, !wrongTop ? 0.5
                    : number <= selected ? 0.2 : number <= 2 * selected ? 0.21 : 0.1);
        }
        return new CandidateScores(values);
    }

    @Test
    void topNumbersUseAscendingNumberAsTieBreaker() {
        Map<Integer, Double> scores = Map.of(1, 0.5, 2, 0.7, 3, 0.7, 4, 0.4);

        assertThat(PredictionMetricsCalculator.topNumbers(scores, 2)).containsExactly(2, 3);
    }
}
