package com.agentinsight.cacheperformance.impact.model;

import java.math.BigDecimal;

public record CacheImpactModel(
    String model,
    int sessions,
    BigDecimal actualCost,
    BigDecimal withoutCacheCost,
    BigDecimal savings,
    double savingsPercent,
    double cacheEfficiency
) {}
