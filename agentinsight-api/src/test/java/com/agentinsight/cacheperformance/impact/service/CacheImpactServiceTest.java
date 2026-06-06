package com.agentinsight.cacheperformance.impact.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.cacheperformance.service.CacheEventExtractor;
import com.agentinsight.cacheperformance.service.CacheMetricCalculator;
import com.agentinsight.cacheperformance.service.CachePerformanceService;
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

class CacheImpactServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void calculatesActualWithoutCacheAndSavingsFromCacheMetrics() throws Exception {
        CacheImpactService service = service(List.of(
            thread("session-1", rollout("""
                {"payload":{"info":{"total_token_usage":{"input_tokens":1000000,"cached_input_tokens":400000,"output_tokens":100000,"total_tokens":1100000}}}}
                """).toString(), "gpt-5")
        ));

        var summary = service.summary(null, null, null, null);

        assertThat(summary.actualCost()).isEqualByComparingTo("1.650000");
        assertThat(summary.withoutCacheCost()).isEqualByComparingTo("2.250000");
        assertThat(summary.savings()).isEqualByComparingTo("0.600000");
        assertThat(summary.savingsPercent()).isEqualTo(26.7);
        assertThat(summary.cacheEfficiency()).isEqualTo(40.0);
        assertThat(summary.dataCompleteness().name()).isEqualTo("EXACT");
        assertThat(summary.pricingProfileId()).isEqualTo("default");
    }

    @Test
    void aggregatesImpactByRepositoryModelSessionAndDay() throws Exception {
        CacheImpactService service = service(List.of(
            thread("session-1", rollout("""
                {"payload":{"info":{"total_token_usage":{"input_tokens":1000000,"cached_input_tokens":500000,"output_tokens":0,"total_tokens":1000000}}}}
                """).toString(), "gpt-5"),
            thread("session-2", rollout("""
                {"payload":{"info":{"total_token_usage":{"input_tokens":2000000,"cached_input_tokens":1000000,"output_tokens":0,"total_tokens":2000000}}}}
                """).toString(), "gpt-5")
        ));

        assertThat(service.daily(null, null, null, null)).hasSize(1);
        assertThat(service.repositories("savings", "desc", 10).getFirst().savings()).isEqualByComparingTo("2.250000");
        assertThat(service.models().getFirst().sessions()).isEqualTo(2);
        assertThat(service.sessions("savings", "desc", null, null, null, null, 1, 0).items())
            .extracting("sessionId")
            .containsExactly("session-2");
        assertThat(service.topSavings(1).sessions()).hasSize(1);
    }

    @Test
    void generatesRecommendationForExpensiveLowCacheSessions() throws Exception {
        CacheImpactService service = service(List.of(
            thread("session-1", rollout("""
                {"payload":{"info":{"total_token_usage":{"input_tokens":2000000,"cached_input_tokens":100000,"output_tokens":0,"total_tokens":2000000}}}}
                """).toString(), "gpt-5")
        ));

        var recommendations = service.recommendations();

        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.getFirst().category()).isEqualTo("LOW_CACHE_EFFICIENCY");
        assertThat(recommendations.getFirst().affectedSessionIds()).containsExactly("session-1");
    }

    private Path rollout(String content) throws Exception {
        Path rollout = tempDir.resolve("rollout-" + System.nanoTime() + ".jsonl");
        Files.writeString(rollout, content);
        return rollout;
    }

    private CacheImpactService service(List<RawThread> threads) {
        PricingProperties properties = new PricingProperties(Map.of(
            "gpt-5", new Pricing(new BigDecimal("1.50"), new BigDecimal("0.00"), new BigDecimal("7.50")),
            "default", new Pricing(new BigDecimal("1.50"), new BigDecimal("0.00"), new BigDecimal("7.50"))
        ));
        PricingService pricingService = new PricingService(properties);
        CachePerformanceService cachePerformanceService = new CachePerformanceService(
            new FakeRepository(threads),
            new CacheEventExtractor(),
            new CacheMetricCalculator(),
            pricingService
        );
        return new CacheImpactService(cachePerformanceService, pricingService);
    }

    private RawThread thread(String id, String rolloutPath, String model) {
        return new RawThread(id, rolloutPath, tempDir.toString(), "Title " + id, "git@github.com:owner/repo.git", "main", model, 1_780_000_000_000L, 1_780_000_100_000L, 0);
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
