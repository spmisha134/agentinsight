package com.agentinsight.cacheperformance.impact.model;

import java.math.BigDecimal;

public record CacheImpactRepository(
    String repositoryKey,
    String repositoryLabel,
    int sessions,
    BigDecimal actualCost,
    BigDecimal withoutCacheCost,
    BigDecimal savings,
    double savingsPercent,
    double cacheEfficiency,
    long cachedInputTokens,
    long uncachedInputTokens
) {}
