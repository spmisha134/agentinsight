package com.agentinsight.cacheperformance.impact.model;

import java.util.List;

public record CacheTopSavings(
    List<CacheImpactRepository> repositories,
    List<CacheImpactModel> models,
    List<CacheImpactSession> sessions
) {}
