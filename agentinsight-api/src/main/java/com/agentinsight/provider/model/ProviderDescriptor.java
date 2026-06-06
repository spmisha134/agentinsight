package com.agentinsight.provider.model;

import java.util.List;

public record ProviderDescriptor(
    ProviderType providerType,
    String displayName,
    ProviderSupportStatus supportStatus,
    String defaultHomePath,
    List<ProviderCapability> capabilities
) {}
