package com.agentinsight.contextmemory.model;

import java.util.List;
import java.util.Map;

public record ContextSessionAnalytics(
    String sessionId,
    String title,
    String cwd,
    String repositoryId,
    String model,
    long createdAtMs,
    long updatedAtMs,
    int promptSegments,
    int memoryReferenceCount,
    ContextMetricSummary metrics,
    String dataCompleteness,
    List<MemoryReference> memoryReferences,
    List<ContextWarning> warnings,
    Map<String, Object> evidence
) {}
