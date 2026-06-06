package com.agentinsight.source.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.config.AgentInsightProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexStateRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesHostAbsoluteRolloutPathIntoMountedCodexSource() throws Exception {
        Path codexSource = Files.createDirectory(tempDir.resolve("codex-source"));
        Path sessions = Files.createDirectory(codexSource.resolve("sessions"));
        Path rollout = sessions.resolve("rollout.jsonl");
        Files.writeString(rollout, "{}\n");
        Path state = codexSource.resolve("state_test.sqlite");
        createStateDatabase(state, "/Users/example/.codex/sessions/rollout.jsonl");

        CodexStateRepository repository = new CodexStateRepository(new AgentInsightProperties(
            codexSource,
            tempDir.resolve("lens-data"),
            tempDir.resolve("lens-data/agentinsight.sqlite")
        ));

        var threads = repository.findThreads();

        assertThat(threads).hasSize(1);
        assertThat(threads.getFirst().rolloutPath()).isEqualTo(rollout.toString());
    }

    private void createStateDatabase(Path state, String rolloutPath) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + state)) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TABLE threads (
                      id TEXT,
                      rollout_path TEXT,
                      cwd TEXT,
                      title TEXT,
                      git_origin_url TEXT,
                      git_branch TEXT,
                      model TEXT,
                      created_at_ms INTEGER,
                      updated_at_ms INTEGER,
                      tokens_used INTEGER,
                      archived INTEGER
                    )
                    """);
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO threads (
                      id, rollout_path, cwd, title, git_origin_url, git_branch, model,
                      created_at_ms, updated_at_ms, tokens_used, archived
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, "session-1");
                statement.setString(2, rolloutPath);
                statement.setString(3, "/workspace/project");
                statement.setString(4, "Session");
                statement.setString(5, "git@github.com:owner/repo.git");
                statement.setString(6, "main");
                statement.setString(7, "gpt-5");
                statement.setLong(8, 1_780_000_000_000L);
                statement.setLong(9, 1_780_000_100_000L);
                statement.setLong(10, 100L);
                statement.setInt(11, 0);
                statement.executeUpdate();
            }
        }
    }
}
