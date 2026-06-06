package com.agentinsight.toolanalytics.model;

import java.util.Map;

public record ToolWarning(
    String severity,
    String code,
    String message,
    Map<String, Object> evidence
) {}
