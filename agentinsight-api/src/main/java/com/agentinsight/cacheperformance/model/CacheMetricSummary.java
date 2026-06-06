package com.agentinsight.cacheperformance.model;

import java.math.BigDecimal;

public record CacheMetricSummary(
    long inputTokens,
    long cachedInputTokens,
    long uncachedInputTokens,
    long outputTokens,
    long totalTokens,
    double cacheHitRatio,
    double uncachedInputRatio,
    BigDecimal estimatedCacheSavings,
    BigDecimal estimatedAdditionalSavingsOpportunity,
    String pricingStatus,
    String metricCompleteness
) {}
