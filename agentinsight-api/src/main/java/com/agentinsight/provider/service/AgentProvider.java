package com.agentinsight.provider.service;

import com.agentinsight.provider.model.*;
import java.util.List;

public interface AgentProvider {
    ProviderType providerType();

    ProviderDescriptor descriptor();

    ProviderHealth healthCheck(ProviderConfiguration config);

    ProviderDiscoveryResult discover(ProviderConfiguration config);

    List<DiscoveredSession> discoverSessions(ProviderConfiguration config);

    NormalizedSession loadSession(ProviderConfiguration config, DiscoveredSession session);

    List<NormalizedMessage> loadMessages(ProviderConfiguration config, DiscoveredSession session);

    List<NormalizedTokenEvent> loadTokenEvents(ProviderConfiguration config, DiscoveredSession session);

    List<NormalizedToolEvent> loadToolEvents(ProviderConfiguration config, DiscoveredSession session);

    List<NormalizedCommandEvent> loadCommandEvents(ProviderConfiguration config, DiscoveredSession session);
}
