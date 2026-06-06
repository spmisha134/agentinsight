package com.agentinsight.contextmemory.model;

public record MemoryReference(
    String type,
    String label,
    int lineNumber,
    String confidence
) {}
