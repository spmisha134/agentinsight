package com.agentinsight.cacheperformance.model;

import com.agentinsight.session.model.TokenUsage;
import java.util.Map;

public record CacheTokenEvent(
    String sessionId,
    int lineNumber,
    TokenUsage usage,
    Map<String, Object> evidence
) {}
