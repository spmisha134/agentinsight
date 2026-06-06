package com.agentinsight.toolanalytics.model;

public record ToolUsageStat(
    String toolName,
    String toolType,
    int calls,
    int failures,
    double failureRate,
    long outputBytes,
    Long averageLatencyMs
) {}
