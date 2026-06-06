package com.agentinsight.toolanalytics.model;

import java.util.List;
import java.util.Map;

public record ToolSessionAnalytics(
    String sessionId,
    String title,
    String cwd,
    String repositoryId,
    String model,
    long createdAtMs,
    long updatedAtMs,
    ToolMetricSummary metrics,
    String dataCompleteness,
    List<ToolUsageStat> tools,
    List<ToolWarning> warnings,
    Map<String, Object> evidence
) {}
