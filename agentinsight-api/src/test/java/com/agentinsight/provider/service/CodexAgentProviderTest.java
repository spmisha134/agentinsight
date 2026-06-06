package com.agentinsight.provider.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.provider.model.ProviderConfiguration;
import com.agentinsight.provider.model.ProviderHealthStatus;
import com.agentinsight.provider.model.ProviderType;
import com.agentinsight.source.sqlite.CodexStateRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexAgentProviderTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsCodexHomeWithReadableStateAndRollouts() throws Exception {
        Path codexHome = Files.createDirectory(tempDir.resolve("codex"));
        Files.writeString(codexHome.resolve("history.jsonl"), "{}\n");
        Path sessions = Files.createDirectories(codexHome.resolve("sessions/2026/06/06"));
        Files.writeString(sessions.resolve("rollout-session-1.jsonl"), "{}\n");
        createStateDatabase(codexHome.resolve("state_test.sqlite"));
        CodexAgentProvider provider = provider(codexHome);

        var health = provider.healthCheck(new ProviderConfiguration(ProviderType.CODEX, "Codex", codexHome.toString()));
        var discovery = provider.discover(new ProviderConfiguration(ProviderType.CODEX, "Codex", codexHome.toString()));

        assertThat(health.status()).isEqualTo(ProviderHealthStatus.READY);
        assertThat(health.sessionsFound()).isEqualTo(1);
        assertThat(discovery.detected()).isTrue();
    }

    @Test
    void reportsPlannedProviderAsUnsupportedThroughServiceValidation() {
        ProviderService service = new ProviderService(
            new ProviderRegistry(),
            new ProviderInstanceRepository(properties(tempDir.resolve("codex"))),
            java.util.List.of(),
            properties(tempDir.resolve("codex"))
        );

        var health = service.validate(new com.agentinsight.provider.model.ProviderSelectionRequest(
            ProviderType.CLAUDE_CODE,
            "Claude Code",
            tempDir.resolve("claude").toString()
        ));

        assertThat(health.status()).isEqualTo(ProviderHealthStatus.UNSUPPORTED);
    }

    private CodexAgentProvider provider(Path codexHome) {
        AgentInsightProperties properties = properties(codexHome);
        ProviderInstanceRepository instanceRepository = new ProviderInstanceRepository(properties);
        return new CodexAgentProvider(
            new ProviderRegistry(),
            new CodexStateRepository(properties, instanceRepository),
            instanceRepository
        );
    }

    private AgentInsightProperties properties(Path codexHome) {
        return new AgentInsightProperties(
            codexHome,
            tempDir.resolve("lens-data"),
            tempDir.resolve("lens-data/agentinsight.sqlite")
        );
    }

    private void createStateDatabase(Path state) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + state);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE threads (id TEXT)");
            statement.execute("INSERT INTO threads (id) VALUES ('session-1')");
        }
    }
}
