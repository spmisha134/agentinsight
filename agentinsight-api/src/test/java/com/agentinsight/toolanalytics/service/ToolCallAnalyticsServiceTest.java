package com.agentinsight.toolanalytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.source.sqlite.CodexStateRepository;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCallAnalyticsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void summarizesToolCallsWithoutRawSensitiveContent() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"tool_call_started","payload":{"tool_call_id":"call-1","tool_name":"exec","status":"started","arguments":"password=secret","timestamp":"2026-06-04T10:00:00Z"}}
            {"type":"tool_call_result","payload":{"tool_call_id":"call-1","tool_name":"exec","status":"completed","result":"secret output","timestamp":"2026-06-04T10:00:01Z"}}
            """);

        var summary = service(List.of(thread("session-1", rollout.toString()))).summary(null, null, null, null, null);

        assertThat(summary.totalSessions()).isEqualTo(1);
        assertThat(summary.totalToolCalls()).isEqualTo(2);
        assertThat(summary.averageLatencyMs()).isEqualTo(1000L);
        assertThat(summary.tools()).extracting("toolName").contains("exec");
        assertThat(summary.sessions().getFirst().title()).isNull();
        assertThat(summary.toString()).doesNotContain("password=secret").doesNotContain("secret output");
    }

    @Test
    void showsUsefulEmptyState() {
        var summary = service(List.of()).summary(null, null, null, null, null);

        assertThat(summary.totalSessions()).isZero();
        assertThat(summary.totalToolCalls()).isZero();
        assertThat(summary.dataCompleteness()).isEqualTo("not_available");
    }

    @Test
    void marksMalformedRolloutAsPartial() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"tool_call","payload":{"tool_name":"search","status":"started"}}
            {broken}
            """);

        var summary = service(List.of(thread("session-1", rollout.toString()))).summary(null, null, null, null, null);

        assertThat(summary.dataCompleteness()).isEqualTo("partial");
        assertThat(summary.warnings()).contains("malformed_rollout_lines");
    }

    private ToolCallAnalyticsService service(List<RawThread> threads) {
        return new ToolCallAnalyticsService(new FakeRepository(threads), new ToolCallExtractor(), new ToolMetricCalculator());
    }

    private RawThread thread(String id, String rolloutPath) {
        return new RawThread(id, rolloutPath, tempDir.toString(), "Prompt-like title", "git@github.com:owner/repo.git", "main", "gpt-5", 1_780_000_000_000L, 1_780_000_100_000L, 0);
    }

    private static class FakeRepository extends CodexStateRepository {
        private final List<RawThread> threads;

        FakeRepository(List<RawThread> threads) {
            super(new AgentInsightProperties(Path.of("."), Path.of("."), Path.of(".")));
            this.threads = threads;
        }

        @Override
        public List<RawThread> findThreads() {
            return threads;
        }
    }
}
