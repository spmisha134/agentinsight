package com.agentinsight.provider.model;

public record ProviderInstance(
    String id,
    ProviderType providerType,
    String displayName,
    String homePath,
    boolean active,
    ProviderHealthStatus healthStatus,
    ProviderSupportStatus supportStatus,
    Long lastValidatedAtMs,
    long createdAtMs,
    long updatedAtMs
) {}
