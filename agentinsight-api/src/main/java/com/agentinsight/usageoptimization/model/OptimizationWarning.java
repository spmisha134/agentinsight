package com.agentinsight.usageoptimization.model;

import java.util.Map;

public record OptimizationWarning(
    String severity,
    String code,
    String message,
    Map<String, Object> evidence
) {}
