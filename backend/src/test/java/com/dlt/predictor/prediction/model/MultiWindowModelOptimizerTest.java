package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dlt.predictor.lottery.Draw;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiWindowModelOptimizerTest {

    @Test
    void finiteSearchUsesFourWindowsAndOnlyTailTargetsForAcceptance() {
        ModelOptimizationResult result = new MultiWindowModelOptimizer().optimize(MultiWindowTrainingTest.draws(300));

        assertThat(result.summary().evaluatedWindows()).containsExactly(0, 29, 60, 120);
        assertThat(result.summary().developmentDrawCount()).isEqualTo(240);
        assertThat(result.summary().holdoutPeriodCount()).isEqualTo(60);
        assertThat(result.summary().stopReason()).isEqualTo("有限候选搜索完成");
        assertThat(result.logistic().validationPredictions().periods()).hasSize(60)
                .extracting(RollingPredictionSet.PeriodPrediction::targetIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(240, 300).boxed().toList());
        assertThat(result.summary().controlMetrics()).hasSize(5);
    }

    @Test
    void holdoutAnswersCannotChooseWindowOrKernelOrFusionWeights() {
        List<Draw> original = MultiWindowTrainingTest.draws(100);
        List<Draw> changed = new ArrayList<>(original);
        for (int index = 80; index < 100; index++) {
            changed.set(index, MultiWindowTrainingTest.draw(index,
                    List.of(30, 31, 32, 33, 34), List.of(11, 12)));
        }
        MultiWindowModelOptimizer optimizer = new MultiWindowModelOptimizer();

        ModelOptimizationResult first = optimizer.optimize(original);
        ModelOptimizationResult second = optimizer.optimize(changed);

        assertThat(first.summary().evaluatedWindows()).containsExactly(0, 29);
        assertThat(second.summary().selectedWindow()).isEqualTo(first.summary().selectedWindow());
        assertThat(second.vector().artifact().frontParameters()).isEqualTo(first.vector().artifact().frontParameters());
        assertThat(second.vector().artifact().backParameters()).isEqualTo(first.vector().artifact().backParameters());
        assertThat(second.ensemble().artifact()).isEqualTo(first.ensemble().artifact());
        assertThat(second.hybrid().artifact()).isEqualTo(first.hybrid().artifact());
        assertThat(second.logistic().validationPredictions().periods().getFirst())
                .isEqualTo(first.logistic().validationPredictions().periods().getFirst());
    }

    @Test
    void requiresSufficientChronologicalHistory() {
        MultiWindowModelOptimizer optimizer = new MultiWindowModelOptimizer();
        assertThatThrownBy(() -> optimizer.optimize(MultiWindowTrainingTest.draws(99)))
                .isInstanceOf(ModelUnavailableException.class);
        List<Draw> reversed = MultiWindowTrainingTest.draws(100).reversed();
        assertThatThrownBy(() -> optimizer.optimize(reversed)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresEveryZoneToBeatRandomAndNotRegressAgainstControl() {
        Map<PredictionModelType, ModelMetrics> control = metrics(0.8, 0.4);
        Map<PredictionModelType, ModelMetrics> candidate = metrics(0.8, 0.4);
        candidate.put(PredictionModelType.VECTOR_TRANSITION, metric(0.9, 0.4));
        assertThat(OptimizationAcceptance.rejectionReasons(candidate, control)).isEmpty();

        candidate.put(PredictionModelType.DYNAMIC_ENSEMBLE, metric(0.79, 0.4));
        assertThat(OptimizationAcceptance.rejectionReasons(candidate, control))
                .anyMatch(reason -> reason.contains("DYNAMIC_ENSEMBLE") && reason.contains("全历史"));
        candidate.put(PredictionModelType.DYNAMIC_ENSEMBLE, metric(0.9, 0.3));
        assertThat(OptimizationAcceptance.rejectionReasons(candidate, control))
                .anyMatch(reason -> reason.contains("DYNAMIC_ENSEMBLE") && reason.contains("随机"));
        assertThat(OptimizationAcceptance.rejectionReasons(control, control))
                .anyMatch(reason -> reason.contains("没有改善"));
    }

    @Test
    void incompleteAndNonFiniteMetricsCanNeverActivate() {
        Map<PredictionModelType, ModelMetrics> control = metrics(0.8, 0.4);
        Map<PredictionModelType, ModelMetrics> incomplete = metrics(0.9, 0.5);
        incomplete.remove(PredictionModelType.DECAYED_FREQUENCY);
        assertThat(OptimizationAcceptance.rejectionReasons(incomplete, control)).isNotEmpty();
        incomplete = metrics(0.9, 0.5);
        incomplete.put(PredictionModelType.VECTOR_TRANSITION, metric(Double.NaN, 0.5));
        assertThat(OptimizationAcceptance.rejectionReasons(incomplete, control)).isNotEmpty();
    }

    private static Map<PredictionModelType, ModelMetrics> metrics(double front, double back) {
        Map<PredictionModelType, ModelMetrics> values = new EnumMap<>(PredictionModelType.class);
        for (PredictionModelType type : PredictionModelType.values()) {
            values.put(type, metric(front, back));
        }
        return values;
    }

    private static ModelMetrics metric(double front, double back) {
        // Literal theoretical baseline; the acceptance policy must not trust stored flags.
        return new ModelMetrics(0.42, 0.46, front, back,
                0.41, 0.45, 25.0 / 35, 4.0 / 12, false, false);
    }
}
