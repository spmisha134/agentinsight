package com.agentinsight.agentsessionreplay.model;

import java.util.Map;

public record ReplayWarning(
    String severity,
    String code,
    String message,
    Map<String, Object> evidence
) {}
