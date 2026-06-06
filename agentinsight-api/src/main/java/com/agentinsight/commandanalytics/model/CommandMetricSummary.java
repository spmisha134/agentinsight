package com.agentinsight.commandanalytics.model;

public record CommandMetricSummary(
    int totalCommands,
    int successfulCommands,
    int failedCommands,
    int unknownCommands,
    int retryCount,
    long totalDurationMs,
    Long averageDurationMs,
    long totalStdoutBytes,
    long totalStderrBytes,
    double successRate,
    double failureRate,
    double retryRate,
    String metricCompleteness
) {}
