package com.agentinsight.provider.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderSelectionRequest(
    @NotNull ProviderType providerType,
    String displayName,
    @NotBlank String homePath
) {}
