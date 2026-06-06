package com.agentinsight.commandanalytics.model;

public record CommandDailyAnalytics(
    String day,
    int sessions,
    int commands,
    int failedCommands,
    int retryCount,
    long totalDurationMs,
    double failureRate
) {}
