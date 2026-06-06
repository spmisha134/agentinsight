package com.agentinsight.provider.model;

import java.util.List;

public record ProviderHealth(
    ProviderHealthStatus status,
    int sessionsFound,
    List<String> warnings,
    List<String> errors,
    List<ProviderCapability> capabilities
) {}
