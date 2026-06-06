package com.agentinsight.cacheperformance.model;

import java.util.Map;

public record CacheWarning(
    String severity,
    String code,
    String message,
    Map<String, Object> evidence
) {}
