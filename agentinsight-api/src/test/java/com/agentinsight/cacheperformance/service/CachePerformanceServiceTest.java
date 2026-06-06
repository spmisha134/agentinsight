package com.agentinsight.cacheperformance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.cost.model.Pricing;
import com.agentinsight.cost.service.PricingProperties;
import com.agentinsight.cost.service.PricingService;
import com.agentinsight.source.sqlite.CodexStateRepository;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachePerformanceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void summarizesCachedAndUncachedInputTokens() throws Exception {
        Path rollout = rollout("""
            {"payload":{"info":{"total_token_usage":{"input_tokens":10000,"cached_input_tokens":4000,"output_tokens":500,"total_tokens":10500}}}}
            """);

        var summary = service(List.of(thread("session-1", rollout.toString(), "gpt-5"))).summary(null, null, null, null);

        assertThat(summary.totalSessions()).isEqualTo(1);
        assertThat(summary.inputTokens()).isEqualTo(10_000);
        assertThat(summary.cachedInputTokens()).isEqualTo(4_000);
        assertThat(summary.cacheHitRatio()).isEqualTo(0.4);
        assertThat(summary.estimatedCacheSavings()).isEqualByComparingTo("0.004500");
        assertThat(summary.sessions().getFirst().title()).isEqualTo("Prompt-like title");
        assertThat(summary.toString()).doesNotContain("input_tokens").doesNotContain("cached_input_tokens");
    }

    @Test
    void showsUsefulEmptyStateBeforeImport() {
        var summary = service(List.of()).summary(null, null, null, null);

        assertThat(summary.totalSessions()).isZero();
        assertThat(summary.dataCompleteness()).isEqualTo("not_available");
    }

    @Test
    void marksMalformedRolloutAsPartial() throws Exception {
        Path rollout = rollout("""
            {"payload":{"info":{"total_token_usage":{"input_tokens":1000,"cached_input_tokens":100,"total_tokens":1000}}}}
            {broken}
            """);

        var summary = service(List.of(thread("session-1", rollout.toString(), "gpt-5"))).summary(null, null, null, null);

        assertThat(summary.dataCompleteness()).isEqualTo("partial");
        assertThat(summary.warnings()).contains("malformed_rollout_lines");
    }

    @Test
    void handlesLargeSessionsDeterministically() throws Exception {
        Path rollout = rollout("""
            {"payload":{"info":{"total_token_usage":{"input_tokens":2500000,"cached_input_tokens":2000000,"output_tokens":20000,"total_tokens":2520000}}}}
            """);

        var session = service(List.of(thread("session-1", rollout.toString(), "gpt-5"))).session("session-1").orElseThrow();

        assertThat(session.metrics().cachedInputTokens()).isEqualTo(2_000_000);
        assertThat(session.metrics().estimatedCacheSavings()).isEqualByComparingTo("2.250000");
        assertThat(session.warnings()).extracting("code").doesNotContain("low_cache_hit_ratio");
    }

    private Path rollout(String content) throws Exception {
        Path rollout = tempDir.resolve("rollout-" + System.nanoTime() + ".jsonl");
        Files.writeString(rollout, content);
        return rollout;
    }

    private CachePerformanceService service(List<RawThread> threads) {
        PricingProperties properties = new PricingProperties(Map.of(
            "gpt-5", new Pricing(new BigDecimal("1.25"), new BigDecimal("0.125"), new BigDecimal("10.0")),
            "default", new Pricing(new BigDecimal("1.25"), new BigDecimal("0.125"), new BigDecimal("10.0"))
        ));
        return new CachePerformanceService(new FakeRepository(threads), new CacheEventExtractor(), new CacheMetricCalculator(), new PricingService(properties));
    }

    private RawThread thread(String id, String rolloutPath, String model) {
        return new RawThread(id, rolloutPath, tempDir.toString(), "Prompt-like title", "git@github.com:owner/repo.git", "main", model, 1_780_000_000_000L, 1_780_000_100_000L, 0);
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
