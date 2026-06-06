package com.agentinsight.provider.model;

public record DiscoveredSession(
    String providerSessionId,
    String sourcePath
) {}
