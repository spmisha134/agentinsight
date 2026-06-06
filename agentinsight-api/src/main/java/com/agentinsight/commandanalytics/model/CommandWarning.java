package com.agentinsight.commandanalytics.model;

import java.util.Map;

public record CommandWarning(
    String severity,
    String code,
    String message,
    Map<String, Object> evidence
) {}
