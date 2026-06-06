package com.agentinsight.provider.model;

public record ProviderConfiguration(
    ProviderType providerType,
    String displayName,
    String homePath
) {}
