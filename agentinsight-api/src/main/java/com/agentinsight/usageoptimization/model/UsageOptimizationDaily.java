package com.agentinsight.usageoptimization.model;

import java.math.BigDecimal;

public record UsageOptimizationDaily(
    String day,
    int sessions,
    int signals,
    int highSeveritySignals,
    long impactTokens,
    BigDecimal impactCost
) {}
