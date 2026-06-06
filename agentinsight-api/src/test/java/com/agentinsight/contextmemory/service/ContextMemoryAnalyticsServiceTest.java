package com.agentinsight.contextmemory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.source.sqlite.CodexStateRepository;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextMemoryAnalyticsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void summarizesSessionsWithoutReturningRawPromptContent() throws Exception {
        String repeated = "Paste this same implementation context with enough words to be counted as repeated context for deterministic analytics.";
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"%s AGENTS.md"}}
            {"type":"message","payload":{"role":"user","content":"%s AGENTS.md"}}
            """.formatted(repeated, repeated));
        var service = service(List.of(thread("session-1", rollout.toString())));

        var summary = service.summary(null, null, null, null);

        assertThat(summary.totalSessions()).isEqualTo(1);
        assertThat(summary.repeatedContextTokens()).isGreaterThan(0);
        assertThat(summary.dataCompleteness()).isEqualTo("exact");
        assertThat(summary.sessions().getFirst().memoryReferences()).extracting("label").contains("AGENTS.md");
        assertThat(summary.sessions().getFirst().title()).isNull();
        assertThat(summary.toString()).doesNotContain(repeated);
    }

    @Test
    void exposesUsefulEmptyStateWhenNoSessionsExist() {
        var summary = service(List.of()).summary(null, null, null, null);

        assertThat(summary.totalSessions()).isZero();
        assertThat(summary.dataCompleteness()).isEqualTo("not_available");
        assertThat(summary.totalContextTokens()).isZero();
    }

    @Test
    void marksMalformedSessionAsPartial() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"valid context"}}
            {broken}
            """);
        var summary = service(List.of(thread("session-1", rollout.toString()))).summary(null, null, null, null);

        assertThat(summary.dataCompleteness()).isEqualTo("partial");
        assertThat(summary.warnings()).contains("malformed_rollout_lines");
    }

    private ContextMemoryAnalyticsService service(List<RawThread> threads) {
        return new ContextMemoryAnalyticsService(new FakeRepository(threads), new ContextExtractor(), new ContextMetricCalculator());
    }

    private RawThread thread(String id, String rolloutPath) {
        return new RawThread(id, rolloutPath, tempDir.toString(), "Session " + id, "git@github.com:owner/repo.git", "main", "gpt-5", 1_780_000_000_000L, 1_780_000_100_000L, 0);
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
