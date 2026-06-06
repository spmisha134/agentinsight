package com.agentinsight.usageoptimization.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record OptimizationSession(
    String sessionId,
    String title,
    String cwd,
    String repositoryId,
    String model,
    long createdAtMs,
    long updatedAtMs,
    String dataCompleteness,
    int signalCount,
    long impactTokens,
    BigDecimal impactCost,
    List<OptimizationSignal> signals,
    Map<String, Object> evidence
) {}
