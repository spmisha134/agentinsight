package com.agentinsight.usageoptimization.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UsageOptimizationSummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    int totalSignals,
    int highSeveritySignals,
    int mediumSeveritySignals,
    int lowSeveritySignals,
    long totalImpactTokens,
    BigDecimal totalImpactCost,
    List<String> warnings,
    List<OptimizationSignal> signals,
    List<OptimizationSession> sessions
) {}
