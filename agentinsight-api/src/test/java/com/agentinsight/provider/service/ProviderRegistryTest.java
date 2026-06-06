package com.agentinsight.provider.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.provider.model.ProviderCapability;
import com.agentinsight.provider.model.ProviderSupportStatus;
import com.agentinsight.provider.model.ProviderType;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
    @Test
    void exposesSupportedCodexAndPlannedFutureProviders() {
        ProviderRegistry registry = new ProviderRegistry();

        assertThat(registry.require(ProviderType.CODEX).supportStatus()).isEqualTo(ProviderSupportStatus.SUPPORTED);
        assertThat(registry.require(ProviderType.CODEX).capabilities()).contains(
            ProviderCapability.SESSION_DISCOVERY,
            ProviderCapability.TOKEN_USAGE,
            ProviderCapability.TOOL_CALLS,
            ProviderCapability.COMMANDS
        );
        assertThat(registry.require(ProviderType.CLAUDE_CODE).supportStatus()).isEqualTo(ProviderSupportStatus.PLANNED);
        assertThat(registry.require(ProviderType.GEMINI_CLI).supportStatus()).isEqualTo(ProviderSupportStatus.PLANNED);
    }
}
