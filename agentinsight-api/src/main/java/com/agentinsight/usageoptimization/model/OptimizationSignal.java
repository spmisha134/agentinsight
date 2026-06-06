package com.agentinsight.usageoptimization.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OptimizationSignal(
    String id,
    String category,
    String severity,
    String title,
    String recommendation,
    String status,
    long impactTokens,
    BigDecimal impactCost,
    List<String> affectedSessions,
    String repositoryId,
    String model,
    Instant generatedAt,
    Map<String, Object> evidence
) {}
