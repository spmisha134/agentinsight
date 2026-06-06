package com.agentinsight.toolanalytics.model;

public record ToolDailyAnalytics(
    String day,
    int sessions,
    int toolCalls,
    int failedToolCalls,
    int retryCount,
    double failureRate
) {}
