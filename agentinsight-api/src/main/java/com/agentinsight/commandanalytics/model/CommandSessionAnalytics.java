package com.agentinsight.commandanalytics.model;

import java.util.List;
import java.util.Map;

public record CommandSessionAnalytics(
    String sessionId,
    String title,
    String cwd,
    String repositoryId,
    String model,
    long createdAtMs,
    long updatedAtMs,
    CommandMetricSummary metrics,
    String dataCompleteness,
    List<CommandCategoryStat> categories,
    List<CommandEvent> commands,
    List<CommandWarning> warnings,
    Map<String, Object> evidence
) {}
