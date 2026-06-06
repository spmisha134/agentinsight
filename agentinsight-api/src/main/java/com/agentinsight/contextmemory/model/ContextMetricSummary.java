package com.agentinsight.contextmemory.model;

public record ContextMetricSummary(
    long totalContextTokens,
    long repeatedContextTokens,
    long cacheableContextTokens,
    long contextGrowthTokens,
    double usefulContextRatio,
    double repeatedContextRatio,
    String metricCompleteness
) {}
