package com.agentinsight.toolanalytics.model;

import java.util.Map;

public record NormalizedToolCall(
    String sessionId,
    int lineNumber,
    String toolCallId,
    String toolName,
    String toolType,
    String status,
    Long eventTimeMs,
    int argumentsSizeBytes,
    int outputSizeBytes,
    String argumentsHash,
    String outputHash,
    Map<String, Object> evidence
) {}
