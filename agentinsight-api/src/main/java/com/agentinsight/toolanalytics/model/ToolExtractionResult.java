package com.agentinsight.toolanalytics.model;

import java.util.List;

public record ToolExtractionResult(
    String sessionId,
    List<NormalizedToolCall> toolCalls,
    int malformedLines,
    boolean rolloutPathPresent,
    boolean rolloutReadable,
    List<String> warnings
) {}
