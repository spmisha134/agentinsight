package com.agentinsight.provider.model;

public record NormalizedCommandEvent(
    String id,
    String sessionId,
    ProviderType providerType,
    String command,
    long timestamp,
    DataCompleteness dataCompleteness
) {}
