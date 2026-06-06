package com.agentinsight.cacheperformance.service;

import com.agentinsight.cacheperformance.model.CacheMetricSummary;
import com.agentinsight.cacheperformance.model.CacheTokenEvent;
import com.agentinsight.cacheperformance.model.CacheWarning;
import com.agentinsight.cost.model.Pricing;
import com.agentinsight.session.model.TokenUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CacheMetricCalculator {
    public CacheMetricSummary calculate(List<CacheTokenEvent> events, Pricing pricing) {
        if (events.isEmpty()) {
            return new CacheMetricSummary(0, 0, 0, 0, 0, 0.0, 0.0, BigDecimal.ZERO.setScale(6), BigDecimal.ZERO.setScale(6), pricingStatus(pricing), "not_available");
        }

        TokenUsage usage = events.getLast().usage();
        long inputTokens = Math.max(0L, usage.inputTokens());
        long cachedInputTokens = Math.min(Math.max(0L, usage.cachedInputTokens()), inputTokens);
        long uncachedInputTokens = Math.max(0L, inputTokens - cachedInputTokens);
        long outputTokens = Math.max(0L, usage.outputTokens() + usage.reasoningOutputTokens());
        long totalTokens = Math.max(usage.totalTokens(), inputTokens + outputTokens);
        BigDecimal estimatedSavings = savings(cachedInputTokens, pricing);
        BigDecimal opportunity = savings(uncachedInputTokens, pricing);
        String completeness = inputTokens == 0L && totalTokens == 0L ? "not_available" : "exact";

        return new CacheMetricSummary(
            inputTokens,
            cachedInputTokens,
            uncachedInputTokens,
            outputTokens,
            totalTokens,
            ratio(cachedInputTokens, inputTokens),
            ratio(uncachedInputTokens, inputTokens),
            estimatedSavings,
            opportunity,
            pricingStatus(pricing),
            completeness
        );
    }

    public List<CacheWarning> warnings(CacheMetricSummary metrics) {
        if ("not_available".equals(metrics.metricCompleteness())) {
            return List.of(warning("warning", "token_usage_not_available", "Token usage data is not available for this session.", Map.of()));
        }
        if (metrics.inputTokens() >= 1_000L && metrics.cacheHitRatio() < 0.10) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("inputTokens", metrics.inputTokens());
            evidence.put("cachedInputTokens", metrics.cachedInputTokens());
            evidence.put("cacheHitRatio", metrics.cacheHitRatio());
            return List.of(warning("warning", "low_cache_hit_ratio", "Large session has a low cached input token ratio.", evidence));
        }
        return List.of();
    }

    private CacheWarning warning(String severity, String code, String message, Map<String, Object> evidence) {
        return new CacheWarning(severity, code, message, evidence);
    }

    private BigDecimal savings(long tokens, Pricing pricing) {
        BigDecimal delta = pricing.inputPerMillion().subtract(pricing.cachedInputPerMillion());
        if (tokens <= 0L || delta.signum() <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(tokens)
            .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.HALF_UP)
            .multiply(delta)
            .setScale(6, RoundingMode.HALF_UP);
    }

    private String pricingStatus(Pricing pricing) {
        if (pricing.inputPerMillion().signum() == 0 && pricing.cachedInputPerMillion().signum() == 0 && pricing.outputPerMillion().signum() == 0) {
            return "missing";
        }
        return "configured";
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }
}
