package com.agentinsight.liveusage.model;

import java.time.Instant;
import java.util.List;

public record LiveUsageSummary(
    Instant generatedAt,
    String dataCompleteness,
    int totalSessions,
    int activeSessions,
    int staleSessions,
    int inactiveSessions,
    int unknownSessions,
    List<String> warnings,
    List<LiveSessionState> sessions
) {}
