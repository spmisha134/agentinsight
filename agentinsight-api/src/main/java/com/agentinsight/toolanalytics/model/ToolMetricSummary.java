package com.agentinsight.toolanalytics.model;

public record ToolMetricSummary(
    int totalToolCalls,
    int successfulToolCalls,
    int failedToolCalls,
    int partialToolCalls,
    int retryCount,
    long totalOutputBytes,
    Long averageLatencyMs,
    double failureRate,
    double retryRate,
    String metricCompleteness
) {}
