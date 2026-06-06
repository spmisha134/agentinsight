package com.agentinsight.provider.model;

public record ProviderDiscoveryResult(
    ProviderType providerType,
    String displayName,
    String homePath,
    boolean detected,
    ProviderSupportStatus supportStatus,
    ProviderHealth health
) {}
