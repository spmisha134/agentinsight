package com.agentinsight.agentsessionreplay.model;

import java.util.List;
import java.util.Map;

public record ReplaySession(
    String sessionId,
    String title,
    String cwd,
    String repositoryId,
    String branch,
    String model,
    long createdAtMs,
    long updatedAtMs,
    ReplayMetricSummary metrics,
    String dataCompleteness,
    int page,
    int size,
    int totalEvents,
    List<ReplayTimelineEvent> events,
    List<ReplayWarning> warnings,
    Map<String, Object> evidence
) {}
