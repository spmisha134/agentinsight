package com.agentinsight.commandanalytics.model;

public record CommandCategoryStat(
    String category,
    int commands,
    int successes,
    int failures,
    int retries,
    double failureRate,
    Long averageDurationMs
) {}
