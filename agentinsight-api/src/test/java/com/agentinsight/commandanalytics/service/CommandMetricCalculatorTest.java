package com.agentinsight.commandanalytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.commandanalytics.model.CommandEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandMetricCalculatorTest {
    private final CommandMetricCalculator calculator = new CommandMetricCalculator();

    @Test
    void calculatesSuccessFailureRetryAndDurationMetrics() {
        var commands = List.of(
            command("hash-a", "test", "succeeded", 1000L, "none"),
            command("hash-a", "test", "failed", 2000L, "none"),
            command("hash-b", "git", "succeeded", null, "none")
        );

        var metrics = calculator.calculate(commands);
        var categories = calculator.categoryStats(commands);

        assertThat(metrics.totalCommands()).isEqualTo(3);
        assertThat(metrics.successfulCommands()).isEqualTo(2);
        assertThat(metrics.failedCommands()).isEqualTo(1);
        assertThat(metrics.retryCount()).isEqualTo(1);
        assertThat(metrics.totalDurationMs()).isEqualTo(3000L);
        assertThat(metrics.averageDurationMs()).isEqualTo(1500L);
        assertThat(metrics.failureRate()).isEqualTo(0.3333);
        assertThat(categories).extracting("category").contains("test", "git");
    }

    @Test
    void detectsRiskyAndRepeatedCommands() {
        var commands = List.of(
            command("hash-a", "filesystem", "failed", null, "high"),
            command("hash-b", "test", "failed", null, "none"),
            command("hash-b", "test", "failed", null, "none"),
            command("hash-b", "test", "failed", null, "none")
        );

        assertThat(calculator.warnings(commands)).extracting("code")
            .contains("risky_command_detected", "repeated_command_detected", "high_command_failure_rate");
    }

    private CommandEvent command(String hash, String category, String status, Long duration, String riskLevel) {
        return new CommandEvent(
            "session-1",
            1,
            "call-1",
            "npm test",
            hash,
            "npm",
            "/repo",
            category,
            status,
            "succeeded".equals(status) ? 0 : 1,
            null,
            null,
            duration,
            10,
            5,
            riskLevel,
            "not_risky",
            Map.of()
        );
    }
}
