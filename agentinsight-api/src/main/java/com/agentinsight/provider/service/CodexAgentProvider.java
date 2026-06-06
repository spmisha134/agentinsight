package com.agentinsight.provider.service;

import com.agentinsight.provider.model.*;
import com.agentinsight.source.sqlite.CodexStateRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class CodexAgentProvider implements AgentProvider {
    private final ProviderRegistry registry;
    private final CodexStateRepository codexStateRepository;
    private final ProviderInstanceRepository providerInstanceRepository;

    public CodexAgentProvider(ProviderRegistry registry, CodexStateRepository codexStateRepository, ProviderInstanceRepository providerInstanceRepository) {
        this.registry = registry;
        this.codexStateRepository = codexStateRepository;
        this.providerInstanceRepository = providerInstanceRepository;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.CODEX;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return registry.require(ProviderType.CODEX);
    }

    @Override
    public ProviderHealth healthCheck(ProviderConfiguration config) {
        Path home = expand(config.homePath());
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (!Files.exists(home)) {
            return new ProviderHealth(ProviderHealthStatus.NOT_FOUND, 0, warnings, List.of("Provider home does not exist: " + home), descriptor().capabilities());
        }
        if (!Files.isDirectory(home) || !Files.isReadable(home)) {
            return new ProviderHealth(ProviderHealthStatus.INVALID, 0, warnings, List.of("Provider home is not a readable directory: " + home), descriptor().capabilities());
        }

        Optional<Path> state = latestStateDatabase(home);
        boolean historyExists = Files.isReadable(home.resolve("history.jsonl"));
        boolean sessionsDirExists = Files.isDirectory(home.resolve("sessions"));
        int sessionsFound = countRolloutFiles(home.resolve("sessions"));

        if (state.isEmpty()) {
            warnings.add("No state_*.sqlite database found.");
        } else if (!threadsTableReadable(state.get())) {
            return new ProviderHealth(ProviderHealthStatus.INVALID, sessionsFound, warnings, List.of("Codex threads table is not readable."), descriptor().capabilities());
        }

        if (!historyExists) {
            warnings.add("history.jsonl was not found.");
        }
        if (!sessionsDirExists) {
            warnings.add("sessions directory was not found.");
        }
        if (sessionsFound == 0) {
            warnings.add("No rollout session files were found.");
        }

        ProviderHealthStatus status = ProviderHealthStatus.READY;
        if (state.isEmpty() && !sessionsDirExists && !historyExists) {
            status = ProviderHealthStatus.INVALID;
            errors.add("Path does not match the expected Codex structure.");
        } else if (state.isEmpty() || sessionsFound == 0 || (!historyExists && !sessionsDirExists)) {
            status = ProviderHealthStatus.PARTIAL;
        }

        return new ProviderHealth(status, sessionsFound, List.copyOf(warnings), List.copyOf(errors), descriptor().capabilities());
    }

    @Override
    public ProviderDiscoveryResult discover(ProviderConfiguration config) {
        ProviderHealth health = healthCheck(config);
        return new ProviderDiscoveryResult(
            ProviderType.CODEX,
            descriptor().displayName(),
            expand(config.homePath()).toString(),
            health.status() != ProviderHealthStatus.NOT_FOUND,
            descriptor().supportStatus(),
            health
        );
    }

    @Override
    public List<DiscoveredSession> discoverSessions(ProviderConfiguration config) {
        Path home = expand(config.homePath());
        try (Stream<Path> stream = Files.walk(home.resolve("sessions"))) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> new DiscoveredSession(path.getFileName().toString().replace(".jsonl", ""), path.toString()))
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public NormalizedSession loadSession(ProviderConfiguration config, DiscoveredSession session) {
        String providerInstanceId = providerInstanceRepository.findActive().map(ProviderInstance::id).orElse(null);
        return new NormalizedSession(
            session.providerSessionId(),
            ProviderType.CODEX,
            providerInstanceId,
            session.providerSessionId(),
            session.providerSessionId(),
            null,
            null,
            null,
            "unknown",
            0,
            0,
            session.sourcePath(),
            DataCompleteness.PARTIAL
        );
    }

    @Override
    public List<NormalizedMessage> loadMessages(ProviderConfiguration config, DiscoveredSession session) {
        return List.of();
    }

    @Override
    public List<NormalizedTokenEvent> loadTokenEvents(ProviderConfiguration config, DiscoveredSession session) {
        return List.of();
    }

    @Override
    public List<NormalizedToolEvent> loadToolEvents(ProviderConfiguration config, DiscoveredSession session) {
        return List.of();
    }

    @Override
    public List<NormalizedCommandEvent> loadCommandEvents(ProviderConfiguration config, DiscoveredSession session) {
        return List.of();
    }

    public List<CodexStateRepository.RawThread> findThreads(ProviderConfiguration config) {
        return codexStateRepository.findThreads(expand(config.homePath()));
    }

    private Optional<Path> latestStateDatabase(Path home) {
        try (Stream<Path> stream = Files.list(home)) {
            return stream
                .filter(path -> path.getFileName().toString().matches("state_.*\\.sqlite"))
                .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean threadsTableReadable(Path state) {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:file:" + state.toAbsolutePath() + "?mode=ro");
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT id FROM threads LIMIT 1").close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int countRolloutFiles(Path sessionsDirectory) {
        if (!Files.isDirectory(sessionsDirectory)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(sessionsDirectory)) {
            return Math.toIntExact(stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .count());
        } catch (Exception e) {
            return 0;
        }
    }

    private Path expand(String homePath) {
        if (homePath == null || homePath.isBlank() || homePath.equals("~/.codex")) {
            return Path.of(System.getProperty("user.home"), ".codex");
        }
        if (homePath.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), homePath.substring(2));
        }
        return Path.of(homePath);
    }
}
