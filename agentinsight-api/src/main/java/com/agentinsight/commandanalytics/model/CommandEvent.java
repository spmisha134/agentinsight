package com.agentinsight.commandanalytics.model;

import java.util.Map;

public record CommandEvent(
    String sessionId,
    int lineNumber,
    String toolCallId,
    String commandPreview,
    String commandHash,
    String commandExecutable,
    String cwd,
    String category,
    String status,
    Integer exitCode,
    Long startedAtMs,
    Long completedAtMs,
    Long durationMs,
    int stdoutSizeBytes,
    int stderrSizeBytes,
    String riskLevel,
    String riskReason,
    Map<String, Object> evidence
) {}
