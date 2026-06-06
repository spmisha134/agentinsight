package com.agentinsight.agentsessionreplay.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReplaySummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    int totalEvents,
    int sessionsWithReplay,
    int malformedLines,
    long totalTokens,
    BigDecimal estimatedCost,
    List<String> warnings,
    List<ReplaySession> sessions
) {}
