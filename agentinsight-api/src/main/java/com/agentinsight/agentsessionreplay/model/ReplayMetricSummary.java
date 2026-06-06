package com.agentinsight.agentsessionreplay.model;

import java.math.BigDecimal;

public record ReplayMetricSummary(
    int totalEvents,
    int messageEvents,
    int toolEvents,
    int commandEvents,
    int fileEvents,
    int tokenEvents,
    long totalTokens,
    long cachedInputTokens,
    BigDecimal estimatedCost,
    String metricCompleteness
) {}
