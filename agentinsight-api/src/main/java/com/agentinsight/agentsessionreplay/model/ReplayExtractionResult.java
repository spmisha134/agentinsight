package com.agentinsight.agentsessionreplay.model;

import java.util.List;

public record ReplayExtractionResult(
    String sessionId,
    List<ReplayTimelineEvent> events,
    int malformedLines,
    boolean rolloutPathPresent,
    boolean rolloutReadable,
    List<String> warnings
) {}
