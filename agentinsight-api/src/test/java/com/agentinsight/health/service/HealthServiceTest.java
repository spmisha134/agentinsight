package com.agentinsight.health.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.config.AgentInsightProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HealthServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsUpWhenSourceDataAndDatabaseAreUsable() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("codex-source"));
        Path data = tempDir.resolve("agentinsight-data");
        Path database = data.resolve("agentinsight.sqlite");

        HealthService service = new HealthService(new AgentInsightProperties(source, data, database));

        var health = service.health();

        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.checks()).containsEntry("codexProviderSource", "OK");
        assertThat(health.checks()).containsEntry("agentInsightData", "OK");
        assertThat(Files.exists(database)).isTrue();
    }

    @Test
    void reportsDownWhenCodexSourceIsMissing() {
        Path missingSource = tempDir.resolve("missing");
        Path data = tempDir.resolve("agentinsight-data");
        Path database = data.resolve("agentinsight.sqlite");

        HealthService service = new HealthService(new AgentInsightProperties(missingSource, data, database));

        var health = service.health();

        assertThat(health.status()).isEqualTo("DOWN");
        assertThat(health.checks()).containsEntry("codexProviderSource", "directory missing");
    }
}
