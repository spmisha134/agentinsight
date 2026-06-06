package com.agentinsight.commandanalytics.model;

import java.util.List;

public record CommandExtractionResult(
    String sessionId,
    List<CommandEvent> commands,
    int malformedLines,
    boolean rolloutPathPresent,
    boolean rolloutReadable,
    List<String> warnings
) {}
