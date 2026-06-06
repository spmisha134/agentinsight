package com.agentinsight.contextmemory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.contextmemory.model.ContextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextMetricCalculatorTest {
    private final ContextMetricCalculator calculator = new ContextMetricCalculator();

    @Test
    void calculatesRepeatedContextRatioFromDuplicateHashes() {
        var segments = List.of(
            segment("hash-a", 100),
            segment("hash-a", 100),
            segment("hash-b", 50)
        );

        var metrics = calculator.calculate(segments);
        var warnings = calculator.repeatedContextWarnings(segments);

        assertThat(metrics.totalContextTokens()).isEqualTo(250);
        assertThat(metrics.repeatedContextTokens()).isEqualTo(100);
        assertThat(metrics.repeatedContextRatio()).isEqualTo(0.4);
        assertThat(metrics.usefulContextRatio()).isEqualTo(0.6);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().evidence()).containsEntry("occurrences", 2);
    }

    @Test
    void ignoresTinyRepeatedSegmentsAsNoise() {
        var segments = List.of(segment("hash-a", 4), segment("hash-a", 4));

        var metrics = calculator.calculate(segments);

        assertThat(metrics.repeatedContextTokens()).isZero();
        assertThat(calculator.repeatedContextWarnings(segments)).isEmpty();
    }

    private ContextSegment segment(String hash, long tokens) {
        return new ContextSegment("session-1", 1, "user", "message", hash, (int) tokens * 4, tokens, List.of());
    }
}
