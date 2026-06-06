package com.agentinsight.contextmemory.model;

import java.time.Instant;
import java.util.List;

public record ContextAnalyticsSummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    long totalContextTokens,
    long repeatedContextTokens,
    long cacheableContextTokens,
    double usefulContextRatio,
    double repeatedContextRatio,
    List<String> warnings,
    List<ContextSessionAnalytics> sessions
) {}
