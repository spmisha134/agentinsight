package com.agentinsight.toolanalytics.model;

import java.time.Instant;
import java.util.List;

public record ToolAnalyticsSummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    int totalToolCalls,
    int failedToolCalls,
    int retryCount,
    long totalOutputBytes,
    Long averageLatencyMs,
    double failureRate,
    double retryRate,
    List<String> warnings,
    List<ToolUsageStat> tools,
    List<ToolSessionAnalytics> sessions
) {}
