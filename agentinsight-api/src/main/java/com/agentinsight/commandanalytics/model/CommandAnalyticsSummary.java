package com.agentinsight.commandanalytics.model;

import java.time.Instant;
import java.util.List;

public record CommandAnalyticsSummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    int totalCommands,
    int failedCommands,
    int retryCount,
    long totalDurationMs,
    Long averageDurationMs,
    double successRate,
    double failureRate,
    double retryRate,
    List<String> warnings,
    List<CommandCategoryStat> categories,
    List<CommandSessionAnalytics> sessions
) {}
