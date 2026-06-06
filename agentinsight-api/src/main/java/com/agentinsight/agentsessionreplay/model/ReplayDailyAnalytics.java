package com.agentinsight.agentsessionreplay.model;

import java.math.BigDecimal;

public record ReplayDailyAnalytics(
    String day,
    int sessions,
    int events,
    int messageEvents,
    int toolEvents,
    int commandEvents,
    long totalTokens,
    BigDecimal estimatedCost
) {}
