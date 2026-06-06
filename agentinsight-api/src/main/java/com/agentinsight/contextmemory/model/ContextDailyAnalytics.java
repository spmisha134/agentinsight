package com.agentinsight.contextmemory.model;

public record ContextDailyAnalytics(
    String day,
    int sessions,
    long totalContextTokens,
    long repeatedContextTokens,
    long cacheableContextTokens,
    double repeatedContextRatio
) {}
