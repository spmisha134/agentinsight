package com.agentinsight.cacheperformance.model;

import java.util.List;

public record CacheExtractionResult(
    String sessionId,
    List<CacheTokenEvent> tokenEvents,
    int malformedLines,
    boolean rolloutPathPresent,
    boolean rolloutReadable,
    List<String> warnings
) {}
