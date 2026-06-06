package com.agentinsight.provider.model;

public record NormalizedMessage(
    String id,
    String sessionId,
    ProviderType providerType,
    String role,
    String content,
    long timestamp,
    DataCompleteness dataCompleteness
) {}
