package com.agentinsight.source.sqlite;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.provider.service.ProviderInstanceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class CodexStateRepository {
    private final AgentInsightProperties properties;
    private final ProviderInstanceRepository providerInstanceRepository;

    public CodexStateRepository(AgentInsightProperties properties) {
        this(properties, null);
    }

    @Autowired
    public CodexStateRepository(AgentInsightProperties properties, ProviderInstanceRepository providerInstanceRepository) {
        this.properties = properties;
        this.providerInstanceRepository = providerInstanceRepository;
    }

    public List<RawThread> findThreads() {
        return findThreads(activeCodexSourcePath());
    }

    public List<RawThread> findThreads(Path sourcePath) {
        Path db = latestStateDatabase(sourcePath);
        if (db == null) {
            return List.of();
        }
        String sql = """
            SELECT id, rollout_path, cwd, title, git_origin_url, git_branch, model,
                   created_at_ms, updated_at_ms, tokens_used
            FROM threads
            WHERE archived = 0
            ORDER BY updated_at_ms DESC
            LIMIT 500
            """;
        try (Connection connection = DriverManager.getConnection(readOnlyJdbcUrl(db));
             var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<RawThread> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new RawThread(
                    rs.getString("id"),
                    resolveRolloutPath(sourcePath, rs.getString("rollout_path")),
                    rs.getString("cwd"),
                    rs.getString("title"),
                    rs.getString("git_origin_url"),
                    rs.getString("git_branch"),
                    rs.getString("model"),
                    rs.getLong("created_at_ms"),
                    rs.getLong("updated_at_ms"),
                    rs.getLong("tokens_used")
                ));
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveRolloutPath(Path sourcePath, String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return rolloutPath;
        }
        Path original = Path.of(rolloutPath);
        if (Files.exists(original)) {
            return original.toString();
        }
        Path fromCodexHome = resolveAfterMarker(rolloutPath, ".codex");
        if (fromCodexHome != null && Files.exists(sourcePath.resolve(fromCodexHome))) {
            return sourcePath.resolve(fromCodexHome).toString();
        }
        Path fromSessions = resolveIncludingMarker(rolloutPath, "sessions");
        if (fromSessions != null && Files.exists(sourcePath.resolve(fromSessions))) {
            return sourcePath.resolve(fromSessions).toString();
        }
        return rolloutPath;
    }

    private Path resolveAfterMarker(String rawPath, String marker) {
        String normalized = rawPath.replace('\\', '/');
        String token = "/" + marker + "/";
        int index = normalized.indexOf(token);
        if (index < 0) {
            return null;
        }
        return Path.of(normalized.substring(index + token.length()));
    }

    private Path resolveIncludingMarker(String rawPath, String marker) {
        String normalized = rawPath.replace('\\', '/');
        String token = "/" + marker + "/";
        int index = normalized.indexOf(token);
        if (index < 0) {
            return null;
        }
        return Path.of(normalized.substring(index + 1));
    }

    private Path latestStateDatabase(Path sourcePath) {
        try (var stream = Files.list(sourcePath)) {
            return stream
                .filter(path -> path.getFileName().toString().matches("state_.*\\.sqlite"))
                .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Path activeCodexSourcePath() {
        if (providerInstanceRepository == null) {
            return properties.codexSourcePath();
        }
        return providerInstanceRepository.findActive()
            .filter(instance -> instance.providerType().name().equals("CODEX"))
            .map(instance -> Path.of(instance.homePath()))
            .orElse(properties.codexSourcePath());
    }

    private String readOnlyJdbcUrl(Path db) {
        return "jdbc:sqlite:file:" + db.toAbsolutePath() + "?mode=ro";
    }

    public record RawThread(
        String id,
        String rolloutPath,
        String cwd,
        String title,
        String repositoryUrl,
        String branch,
        String model,
        long createdAtMs,
        long updatedAtMs,
        long tokensUsed
    ) {}
}
