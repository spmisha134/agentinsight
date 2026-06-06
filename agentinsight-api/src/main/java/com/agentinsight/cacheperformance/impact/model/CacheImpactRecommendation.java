package com.agentinsight.cacheperformance.impact.model;

import java.math.BigDecimal;
import java.util.List;

public record CacheImpactRecommendation(
    String id,
    String category,
    String severity,
    String title,
    String description,
    String recommendation,
    BigDecimal impactCost,
    long impactTokens,
    List<String> affectedSessionIds,
    List<String> affectedRepositoryKeys
) {}
