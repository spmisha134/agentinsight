package com.agentinsight.contextmemory.model;

import java.util.List;

public record ContextSegment(
    String sessionId,
    int lineNumber,
    String role,
    String source,
    String contentHash,
    int characterCount,
    long estimatedTokens,
    List<MemoryReference> memoryReferences
) {}
