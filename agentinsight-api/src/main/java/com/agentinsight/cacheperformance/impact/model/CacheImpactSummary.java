package com.agentinsight.cacheperformance.impact.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CacheImpactSummary(
    String currency,
    String pricingProfileId,
    BigDecimal actualCost,
    BigDecimal withoutCacheCost,
    BigDecimal savings,
    double savingsPercent,
    double cacheEfficiency,
    long inputTokens,
    long cachedInputTokens,
    long uncachedInputTokens,
    long outputTokens,
    long reasoningOutputTokens,
    Long estimatedTimeSavedMs,
    CacheImpactCompleteness dataCompleteness,
    Instant generatedAt
) {}
