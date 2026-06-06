package com.agentinsight.provider.service;

import com.agentinsight.provider.model.ProviderCapability;
import com.agentinsight.provider.model.ProviderDescriptor;
import com.agentinsight.provider.model.ProviderSupportStatus;
import com.agentinsight.provider.model.ProviderType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProviderRegistry {
    private final Map<ProviderType, ProviderDescriptor> descriptors;

    public ProviderRegistry() {
        descriptors = new EnumMap<>(ProviderType.class);
        descriptors.put(ProviderType.CODEX, new ProviderDescriptor(
            ProviderType.CODEX,
            "Codex",
            ProviderSupportStatus.SUPPORTED,
            "~/.codex",
            List.of(
                ProviderCapability.SESSION_DISCOVERY,
                ProviderCapability.MESSAGE_HISTORY,
                ProviderCapability.TOKEN_USAGE,
                ProviderCapability.CACHE_USAGE,
                ProviderCapability.TOOL_CALLS,
                ProviderCapability.COMMANDS,
                ProviderCapability.REPLAY_TIMELINE,
                ProviderCapability.CONTEXT_USAGE,
                ProviderCapability.COST_ESTIMATION,
                ProviderCapability.LIVE_STATUS
            )
        ));
        descriptors.put(ProviderType.CLAUDE_CODE, new ProviderDescriptor(
            ProviderType.CLAUDE_CODE,
            "Claude Code",
            ProviderSupportStatus.PLANNED,
            "~/.claude",
            List.of()
        ));
        descriptors.put(ProviderType.GEMINI_CLI, new ProviderDescriptor(
            ProviderType.GEMINI_CLI,
            "Gemini CLI",
            ProviderSupportStatus.PLANNED,
            "",
            List.of()
        ));
        descriptors.put(ProviderType.CUSTOM, new ProviderDescriptor(
            ProviderType.CUSTOM,
            "Custom Provider",
            ProviderSupportStatus.EXPERIMENTAL,
            "",
            List.of()
        ));
    }

    public List<ProviderDescriptor> descriptors() {
        return descriptors.values().stream().toList();
    }

    public Optional<ProviderDescriptor> find(ProviderType providerType) {
        return Optional.ofNullable(descriptors.get(providerType));
    }

    public ProviderDescriptor require(ProviderType providerType) {
        return find(providerType).orElseThrow(() -> new IllegalArgumentException("Unknown provider type: " + providerType));
    }
}
