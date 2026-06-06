package com.agentinsight.provider.model;

public record NormalizedToolEvent(
    String id,
    String sessionId,
    ProviderType providerType,
    String toolName,
    long timestamp,
    DataCompleteness dataCompleteness
) {}
