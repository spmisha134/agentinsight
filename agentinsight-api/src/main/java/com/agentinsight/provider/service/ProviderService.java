package com.agentinsight.provider.service;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.provider.model.*;
import com.agentinsight.source.sqlite.CodexStateRepository;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProviderService {
    private final ProviderRegistry registry;
    private final ProviderInstanceRepository instanceRepository;
    private final Map<ProviderType, AgentProvider> providers;
    private final AgentInsightProperties properties;

    public ProviderService(ProviderRegistry registry, ProviderInstanceRepository instanceRepository, List<AgentProvider> providers, AgentInsightProperties properties) {
        this.registry = registry;
        this.instanceRepository = instanceRepository;
        this.providers = new EnumMap<>(ProviderType.class);
        providers.forEach(provider -> this.providers.put(provider.providerType(), provider));
        this.properties = properties;
    }

    public List<ProviderDescriptor> registry() {
        return registry.descriptors();
    }

    public Optional<ProviderInstance> activeProvider() {
        return instanceRepository.findActive();
    }

    public List<ProviderDiscoveryResult> discover() {
        return registry.descriptors().stream()
            .map(descriptor -> providers.get(descriptor.providerType()) == null
                ? unsupportedDiscovery(descriptor)
                : providers.get(descriptor.providerType()).discover(defaultConfiguration(descriptor)))
            .toList();
    }

    public ProviderHealth validate(ProviderSelectionRequest request) {
        ProviderDescriptor descriptor = registry.require(request.providerType());
        AgentProvider provider = providers.get(request.providerType());
        if (provider == null || descriptor.supportStatus() == ProviderSupportStatus.PLANNED || descriptor.supportStatus() == ProviderSupportStatus.UNAVAILABLE) {
            return new ProviderHealth(ProviderHealthStatus.UNSUPPORTED, 0, List.of(), List.of(descriptor.displayName() + " is not implemented yet."), descriptor.capabilities());
        }
        return provider.healthCheck(new ProviderConfiguration(request.providerType(), displayName(request, descriptor), request.homePath()));
    }

    public ProviderInstance setActive(ProviderSelectionRequest request) {
        ProviderDescriptor descriptor = registry.require(request.providerType());
        if (descriptor.supportStatus() != ProviderSupportStatus.SUPPORTED) {
            throw new IllegalArgumentException(descriptor.displayName() + " is not supported yet.");
        }
        ProviderHealth health = validate(request);
        if (health.status() == ProviderHealthStatus.INVALID || health.status() == ProviderHealthStatus.NOT_FOUND || health.status() == ProviderHealthStatus.UNSUPPORTED) {
            throw new IllegalArgumentException("Provider is not valid: " + String.join(", ", health.errors()));
        }
        return instanceRepository.saveActive(request.providerType(), displayName(request, descriptor), request.homePath(), descriptor.supportStatus(), health);
    }

    public List<CodexStateRepository.RawThread> activeThreads() {
        Optional<ProviderInstance> active = activeProvider();
        if (active.isEmpty()) {
            return List.of();
        }
        AgentProvider provider = providers.get(active.get().providerType());
        if (provider instanceof CodexAgentProvider codexProvider) {
            return codexProvider.findThreads(new ProviderConfiguration(active.get().providerType(), active.get().displayName(), active.get().homePath()));
        }
        return List.of();
    }

    private ProviderDiscoveryResult unsupportedDiscovery(ProviderDescriptor descriptor) {
        return new ProviderDiscoveryResult(
            descriptor.providerType(),
            descriptor.displayName(),
            descriptor.defaultHomePath(),
            false,
            descriptor.supportStatus(),
            new ProviderHealth(ProviderHealthStatus.UNSUPPORTED, 0, List.of(), List.of(descriptor.displayName() + " is not implemented yet."), descriptor.capabilities())
        );
    }

    private ProviderConfiguration defaultConfiguration(ProviderDescriptor descriptor) {
        String homePath = descriptor.providerType() == ProviderType.CODEX
            ? properties.codexSourcePath().toString()
            : descriptor.defaultHomePath();
        return new ProviderConfiguration(descriptor.providerType(), descriptor.displayName(), homePath);
    }

    private String displayName(ProviderSelectionRequest request, ProviderDescriptor descriptor) {
        return request.displayName() == null || request.displayName().isBlank() ? descriptor.displayName() : request.displayName();
    }
}
