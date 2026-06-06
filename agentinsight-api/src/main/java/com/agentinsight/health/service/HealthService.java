package com.agentinsight.health.service;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.health.model.HealthResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final AgentInsightProperties properties;

    public HealthService(AgentInsightProperties properties) {
        this.properties = properties;
    }

    public HealthResponse health() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("codexSource", readableDirectory(properties.codexSourcePath()));
        checks.put("lensData", writableDirectory(properties.lensDataPath()));
        checks.put("database", databaseOpenable(properties.databasePath()));

        String status = checks.values().stream().allMatch("OK"::equals) ? "UP" : "DOWN";
        return new HealthResponse(status, checks);
    }

    public void validateStartup() {
        HealthResponse health = health();
        if (!"UP".equals(health.status())) {
            throw new IllegalStateException("Invalid AgentInsight configuration: " + health.checks());
        }
    }

    private String readableDirectory(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return "directory missing";
        }
        return Files.isReadable(path) ? "OK" : "not readable";
    }

    private String writableDirectory(Path path) {
        try {
            if (path == null) {
                return "directory missing";
            }
            Files.createDirectories(path);
            return Files.isWritable(path) ? "OK" : "not writable";
        } catch (Exception e) {
            return "cannot create";
        }
    }

    private String databaseOpenable(Path path) {
        try {
            if (path == null) {
                return "path missing";
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var ignored = DriverManager.getConnection("jdbc:sqlite:" + path)) {
                return "OK";
            }
        } catch (Exception e) {
            return "cannot open";
        }
    }
}
