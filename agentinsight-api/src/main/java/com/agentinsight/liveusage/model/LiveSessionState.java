package com.agentinsight.liveusage.model;

import java.util.List;
import java.util.Map;

public record LiveSessionState(
    String sessionId,
    String title,
    String cwd,
    String repositoryUrl,
    String branch,
    String model,
    LiveSessionStatus status,
    String statusReason,
    String confidence,
    long latestActivityMs,
    long threadUpdatedAtMs,
    Long rolloutModifiedAtMs,
    Long rolloutSizeBytes,
    Long readFreshnessMs,
    String dataCompleteness,
    List<String> warnings,
    Map<String, Object> evidence
) {}
