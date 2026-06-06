package com.agentinsight.cacheperformance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CachePerformanceSummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    long inputTokens,
    long cachedInputTokens,
    long uncachedInputTokens,
    long totalTokens,
    double cacheHitRatio,
    BigDecimal estimatedCacheSavings,
    BigDecimal estimatedAdditionalSavingsOpportunity,
    List<String> warnings,
    List<CacheSessionAnalytics> sessions
) {}
