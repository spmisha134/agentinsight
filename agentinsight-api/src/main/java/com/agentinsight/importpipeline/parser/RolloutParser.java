package com.agentinsight.importpipeline.parser;

import com.agentinsight.session.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RolloutParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<TokenUsage> latestTokenUsage(Path rolloutPath) {
        if (rolloutPath == null || !Files.exists(rolloutPath)) {
            return Optional.empty();
        }
        TokenUsage latest = null;
        try (BufferedReader reader = Files.newBufferedReader(rolloutPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(line);
                JsonNode info = root.path("payload").path("info").path("total_token_usage");
                if (!info.isMissingNode()) {
                    latest = new TokenUsage(
                        info.path("input_tokens").asLong(0),
                        info.path("cached_input_tokens").asLong(0),
                        info.path("output_tokens").asLong(0),
                        info.path("reasoning_output_tokens").asLong(0),
                        info.path("total_tokens").asLong(0)
                    );
                }
            }
            return Optional.ofNullable(latest);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
