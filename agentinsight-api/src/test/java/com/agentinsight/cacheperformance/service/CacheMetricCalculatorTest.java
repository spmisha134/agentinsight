package com.agentinsight.cacheperformance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.cacheperformance.model.CacheTokenEvent;
import com.agentinsight.cost.model.Pricing;
import com.agentinsight.session.model.TokenUsage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CacheMetricCalculatorTest {
    private final CacheMetricCalculator calculator = new CacheMetricCalculator();
    private final Pricing pricing = new Pricing(new BigDecimal("1.25"), new BigDecimal("0.125"), new BigDecimal("10.0"));

    @Test
    void calculatesCacheRatiosAndSavingsFromLatestTokenEvent() {
        var event = new CacheTokenEvent("session-1", 1, new TokenUsage(10_000, 4_000, 500, 0, 10_500), Map.of());

        var metrics = calculator.calculate(List.of(event), pricing);

        assertThat(metrics.cacheHitRatio()).isEqualTo(0.4);
        assertThat(metrics.uncachedInputTokens()).isEqualTo(6_000);
        assertThat(metrics.estimatedCacheSavings()).isEqualByComparingTo("0.004500");
        assertThat(metrics.estimatedAdditionalSavingsOpportunity()).isEqualByComparingTo("0.006750");
        assertThat(metrics.pricingStatus()).isEqualTo("configured");
    }

    @Test
    void handlesMissingPricing() {
        var event = new CacheTokenEvent("session-1", 1, new TokenUsage(10_000, 4_000, 0, 0, 10_000), Map.of());

        var metrics = calculator.calculate(List.of(event), Pricing.zero());

        assertThat(metrics.estimatedCacheSavings()).isEqualByComparingTo("0.000000");
        assertThat(metrics.pricingStatus()).isEqualTo("missing");
    }

    @Test
    void flagsLargeLowCacheSessions() {
        var event = new CacheTokenEvent("session-1", 1, new TokenUsage(10_000, 100, 0, 0, 10_000), Map.of());

        var metrics = calculator.calculate(List.of(event), pricing);

        assertThat(calculator.warnings(metrics)).extracting("code").containsExactly("low_cache_hit_ratio");
    }
}
