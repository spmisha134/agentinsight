package com.agentinsight.provider.model;

public record NormalizedTokenEvent(
    String id,
    String sessionId,
    ProviderType providerType,
    long inputTokens,
    long cachedInputTokens,
    long outputTokens,
    long reasoningOutputTokens,
    long totalTokens,
    Long contextWindow,
    long timestamp,
    DataCompleteness dataCompleteness
) {}
