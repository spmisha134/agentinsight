package com.agentinsight.provider.service;

import com.agentinsight.config.AgentInsightProperties;
import com.agentinsight.provider.model.ProviderHealth;
import com.agentinsight.provider.model.ProviderHealthStatus;
import com.agentinsight.provider.model.ProviderInstance;
import com.agentinsight.provider.model.ProviderSupportStatus;
import com.agentinsight.provider.model.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderInstanceRepository {
    private final AgentInsightProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ProviderInstanceRepository(AgentInsightProperties properties) {
        this(properties, new ObjectMapper(), Clock.systemUTC());
    }

    ProviderInstanceRepository(AgentInsightProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Optional<ProviderInstance> findActive() {
        ensureSchema();
        String sql = """
            SELECT id, provider_type, display_name, home_path, active, health_status,
                   support_status, last_validated_at_ms, created_at_ms, updated_at_ms
            FROM provider_instances
            WHERE active = 1
            ORDER BY updated_at_ms DESC
            LIMIT 1
            """;
        try (Connection connection = connection();
             var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(toInstance(rs));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ProviderInstance saveActive(ProviderType providerType, String displayName, String homePath, ProviderSupportStatus supportStatus, ProviderHealth health) {
        ensureSchema();
        long now = clock.millis();
        String id = providerType.name().toLowerCase() + "-" + UUID.nameUUIDFromBytes(homePath.getBytes()).toString();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (var deactivate = connection.prepareStatement("UPDATE provider_instances SET active = 0, updated_at_ms = ?")) {
                deactivate.setLong(1, now);
                deactivate.executeUpdate();
            }
            try (var upsert = connection.prepareStatement("""
                INSERT INTO provider_instances (
                  id, provider_type, display_name, home_path, active, health_status,
                  support_status, last_validated_at_ms, created_at_ms, updated_at_ms
                ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  display_name = excluded.display_name,
                  home_path = excluded.home_path,
                  active = 1,
                  health_status = excluded.health_status,
                  support_status = excluded.support_status,
                  last_validated_at_ms = excluded.last_validated_at_ms,
                  updated_at_ms = excluded.updated_at_ms
                """)) {
                upsert.setString(1, id);
                upsert.setString(2, providerType.name());
                upsert.setString(3, displayName);
                upsert.setString(4, homePath);
                upsert.setString(5, health.status().name());
                upsert.setString(6, supportStatus.name());
                upsert.setLong(7, now);
                upsert.setLong(8, now);
                upsert.setLong(9, now);
                upsert.executeUpdate();
            }
            insertHealth(connection, id, health, now);
            connection.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save active provider", e);
        }
        return new ProviderInstance(id, providerType, displayName, homePath, true, health.status(), supportStatus, now, now, now);
    }

    public void saveHealth(String providerInstanceId, ProviderHealth health) {
        ensureSchema();
        long now = clock.millis();
        try (Connection connection = connection()) {
            insertHealth(connection, providerInstanceId, health, now);
            try (var statement = connection.prepareStatement("""
                UPDATE provider_instances
                SET health_status = ?, last_validated_at_ms = ?, updated_at_ms = ?
                WHERE id = ?
                """)) {
                statement.setString(1, health.status().name());
                statement.setLong(2, now);
                statement.setLong(3, now);
                statement.setString(4, providerInstanceId);
                statement.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save provider health", e);
        }
    }

    private void insertHealth(Connection connection, String providerInstanceId, ProviderHealth health, long now) throws Exception {
        try (var statement = connection.prepareStatement("""
            INSERT INTO provider_health_checks (
              id, provider_instance_id, status, sessions_found, warnings_json, errors_json, checked_at_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, providerInstanceId);
            statement.setString(3, health.status().name());
            statement.setInt(4, health.sessionsFound());
            statement.setString(5, objectMapper.writeValueAsString(health.warnings()));
            statement.setString(6, objectMapper.writeValueAsString(health.errors()));
            statement.setLong(7, now);
            statement.executeUpdate();
        }
    }

    private ProviderInstance toInstance(ResultSet rs) throws Exception {
        long lastValidatedAtMs = rs.getLong("last_validated_at_ms");
        return new ProviderInstance(
            rs.getString("id"),
            ProviderType.valueOf(rs.getString("provider_type")),
            rs.getString("display_name"),
            rs.getString("home_path"),
            rs.getInt("active") == 1,
            ProviderHealthStatus.valueOf(rs.getString("health_status")),
            ProviderSupportStatus.valueOf(rs.getString("support_status")),
            rs.wasNull() ? null : lastValidatedAtMs,
            rs.getLong("created_at_ms"),
            rs.getLong("updated_at_ms")
        );
    }

    private void ensureSchema() {
        try {
            Files.createDirectories(properties.databasePath().getParent());
            try (Connection connection = connection();
                 var statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS provider_instances (
                      id TEXT PRIMARY KEY,
                      provider_type TEXT NOT NULL,
                      display_name TEXT NOT NULL,
                      home_path TEXT NOT NULL,
                      active INTEGER NOT NULL DEFAULT 0,
                      health_status TEXT NOT NULL,
                      support_status TEXT NOT NULL,
                      last_validated_at_ms INTEGER,
                      created_at_ms INTEGER NOT NULL,
                      updated_at_ms INTEGER NOT NULL
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS provider_health_checks (
                      id TEXT PRIMARY KEY,
                      provider_instance_id TEXT NOT NULL,
                      status TEXT NOT NULL,
                      sessions_found INTEGER NOT NULL DEFAULT 0,
                      warnings_json TEXT,
                      errors_json TEXT,
                      checked_at_ms INTEGER NOT NULL,
                      FOREIGN KEY (provider_instance_id) REFERENCES provider_instances(id)
                    )
                    """);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize provider tables", e);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + properties.databasePath());
    }
}
