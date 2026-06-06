package com.agentinsight.toolanalytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.toolanalytics.model.NormalizedToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolMetricCalculatorTest {
    private final ToolMetricCalculator calculator = new ToolMetricCalculator();

    @Test
    void calculatesFailureRetryOutputAndLatencyMetrics() {
        var calls = List.of(
            call("call-1", "exec", "started", 1000L, "args-a", 0),
            call("call-1", "exec", "succeeded", 1600L, null, 200),
            call("call-2", "exec", "failed", 2000L, "args-a", 50)
        );

        var metrics = calculator.calculate(calls);
        var stats = calculator.toolStats(calls);

        assertThat(metrics.totalToolCalls()).isEqualTo(3);
        assertThat(metrics.failedToolCalls()).isEqualTo(1);
        assertThat(metrics.retryCount()).isEqualTo(1);
        assertThat(metrics.totalOutputBytes()).isEqualTo(250);
        assertThat(metrics.averageLatencyMs()).isEqualTo(600L);
        assertThat(metrics.failureRate()).isEqualTo(0.3333);
        assertThat(stats.getFirst().toolName()).isEqualTo("exec");
    }

    @Test
    void detectsRepeatedToolLoop() {
        var calls = List.of(
            call("1", "search", "failed", null, "same", 0),
            call("2", "search", "failed", null, "same", 0),
            call("3", "search", "failed", null, "same", 0),
            call("4", "search", "failed", null, "same", 0)
        );

        assertThat(calculator.warnings(calls)).extracting("code").contains("tool_loop_detected", "high_tool_failure_rate");
    }

    private NormalizedToolCall call(String id, String tool, String status, Long time, String argumentHash, int outputBytes) {
        return new NormalizedToolCall("session-1", 1, id, tool, "command", status, time, 0, outputBytes, argumentHash, null, Map.of());
    }
}
