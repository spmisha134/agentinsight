package com.agentinsight.cacheperformance.model;

import java.math.BigDecimal;

public record CacheDailyAnalytics(
    String day,
    int sessions,
    long inputTokens,
    long cachedInputTokens,
    long uncachedInputTokens,
    double cacheHitRatio,
    BigDecimal estimatedCacheSavings
) {}
