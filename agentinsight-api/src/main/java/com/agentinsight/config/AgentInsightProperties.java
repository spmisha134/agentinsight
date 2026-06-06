package com.agentinsight.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentinsight")
public record AgentInsightProperties(
    Path codexSourcePath,
    Path lensDataPath,
    Path databasePath
) {}
