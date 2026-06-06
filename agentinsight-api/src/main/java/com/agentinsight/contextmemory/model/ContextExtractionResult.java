package com.agentinsight.contextmemory.model;

import java.util.List;

public record ContextExtractionResult(
    String sessionId,
    List<ContextSegment> segments,
    int malformedLines,
    boolean rolloutPathPresent,
    boolean rolloutReadable,
    List<String> warnings
) {}
