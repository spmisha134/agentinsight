package com.agentinsight.cacheperformance.impact.model;

import java.math.BigDecimal;

public record CacheImpactDaily(
    String day,
    BigDecimal actualCost,
    BigDecimal withoutCacheCost,
    BigDecimal savings,
    double savingsPercent,
    double cacheEfficiency,
    long cachedInputTokens,
    long uncachedInputTokens
) {}
