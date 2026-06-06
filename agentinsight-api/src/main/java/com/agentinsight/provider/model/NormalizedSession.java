package com.agentinsight.provider.model;

public record NormalizedSession(
    String id,
    ProviderType providerType,
    String providerInstanceId,
    String externalSessionId,
    String title,
    String repositoryKey,
    String repositoryUrl,
    String cwd,
    String model,
    long createdAt,
    long updatedAt,
    String sourcePath,
    DataCompleteness dataCompleteness
) {}
