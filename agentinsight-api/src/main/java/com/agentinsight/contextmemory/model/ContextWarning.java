package com.agentinsight.contextmemory.model;

import java.util.Map;

public record ContextWarning(
    String severity,
    String code,
    String message,
    Map<String, Object> evidence
) {}
