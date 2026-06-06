package com.agentinsight.cacheperformance.impact.model;

import java.util.List;

public record CacheImpactSessionPage(
    List<CacheImpactSession> items,
    long total,
    int limit,
    int offset
) {}
