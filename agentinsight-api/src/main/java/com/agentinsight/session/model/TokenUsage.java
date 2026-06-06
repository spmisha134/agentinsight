package com.agentinsight.session.model;

public record TokenUsage(
    long inputTokens,
    long cachedInputTokens,
    long outputTokens,
    long reasoningOutputTokens,
    long totalTokens
) {}
