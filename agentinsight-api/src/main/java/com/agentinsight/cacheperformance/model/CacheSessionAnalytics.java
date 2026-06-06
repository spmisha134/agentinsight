package com.agentinsight.cacheperformance.model;

import java.util.List;
import java.util.Map;

public record CacheSessionAnalytics(
    String sessionId,
    String title,
    String cwd,
    String repositoryId,
    String model,
    long createdAtMs,
    long updatedAtMs,
    int tokenEventCount,
    CacheMetricSummary metrics,
    String dataCompleteness,
    List<CacheWarning> warnings,
    Map<String, Object> evidence
) {}
