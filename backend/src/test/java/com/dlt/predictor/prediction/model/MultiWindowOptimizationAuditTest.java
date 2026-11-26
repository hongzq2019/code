package com.dlt.predictor.prediction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiWindowOptimizationAuditTest {
    @TempDir Path directory;

    @Test
    void loadsAllFiveColumnsWithoutStartingAnApplicationOrConnectingToDatabase() throws Exception {
        Path input = directory.resolve("history.tsv");
        Files.write(input, MultiWindowTrainingTest.draws(100).stream().map(draw -> draw.id() + "\t" + draw.issueNo()
                + "\t" + draw.drawDate() + "\t" + draw.numbers().frontText() + "\t" + draw.numbers().backText()).toList());

        assertThat(MultiWindowOptimizationAudit.loadDraws(input)).hasSize(100);
        assertThat(MultiWindowOptimizationAudit.loadDraws(input).getFirst().numbers().front()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void refusesEmptyShortMalformedAndDuplicateHistory() throws Exception {
        Path input = directory.resolve("bad.tsv");
        Files.write(input, List.of());
        assertThatThrownBy(() -> MultiWindowOptimizationAudit.loadDraws(input)).isInstanceOf(IllegalArgumentException.class);
        Files.write(input, List.of("1\tI1\t2026-01-01\t1,2,3,4,5\t1,2"));
        assertThatThrownBy(() -> MultiWindowOptimizationAudit.loadDraws(input)).isInstanceOf(IllegalArgumentException.class);
        Files.write(input, List.of("missing-columns"));
        assertThatThrownBy(() -> MultiWindowOptimizationAudit.loadDraws(input)).isInstanceOf(IllegalArgumentException.class);
        List<String> rows = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            rows.add((index + 1) + "\tI1\t" + java.time.LocalDate.of(2025, 1, 1).plusDays(index) + "\t1,2,3,4,5\t1,2");
        }
        Files.write(input, rows);
        assertThatThrownBy(() -> MultiWindowOptimizationAudit.loadDraws(input)).isInstanceOf(IllegalArgumentException.class);
    }
}
