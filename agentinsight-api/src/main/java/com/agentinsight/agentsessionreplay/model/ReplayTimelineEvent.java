package com.agentinsight.agentsessionreplay.model;

import java.math.BigDecimal;
import java.util.Map;

public record ReplayTimelineEvent(
    String id,
    String sessionId,
    int sequence,
    int lineNumber,
    String eventType,
    String sourceType,
    String actor,
    String title,
    String contentPreview,
    String contentHash,
    boolean redacted,
    Long eventTimeMs,
    String status,
    String toolName,
    String commandPreview,
    String repositoryId,
    String branch,
    String filePath,
    String model,
    Long inputTokens,
    Long cachedInputTokens,
    Long outputTokens,
    Long totalTokens,
    BigDecimal estimatedCost,
    Map<String, Object> evidence
) {}
