package com.agentinsight.cacheperformance.impact.model;

import java.math.BigDecimal;

public record CacheImpactSession(
    String sessionId,
    String title,
    String repositoryLabel,
    String model,
    BigDecimal actualCost,
    BigDecimal withoutCacheCost,
    BigDecimal savings,
    double savingsPercent,
    double cacheEfficiency,
    long inputTokens,
    long cachedInputTokens,
    long uncachedInputTokens,
    long updatedAt
) {}
