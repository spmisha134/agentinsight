package com.agentinsight.cacheperformance.service;

import com.agentinsight.cacheperformance.model.CacheExtractionResult;
import com.agentinsight.cacheperformance.model.CacheTokenEvent;
import com.agentinsight.session.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CacheEventExtractor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CacheExtractionResult extract(String sessionId, String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return new CacheExtractionResult(sessionId, List.of(), 0, false, false, List.of("rollout_path_missing"));
        }

        Path path;
        try {
            path = Path.of(rolloutPath);
        } catch (Exception e) {
            return new CacheExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_path_invalid"));
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return new CacheExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_file_not_readable"));
        }

        List<CacheTokenEvent> events = new ArrayList<>();
        int malformedLines = 0;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(line);
                    int currentLineNumber = lineNumber;
                    tokenUsage(root).ifPresent(usage -> events.add(event(sessionId, currentLineNumber, usage)));
                } catch (Exception e) {
                    malformedLines++;
                }
            }
        } catch (Exception e) {
            return new CacheExtractionResult(sessionId, events, malformedLines, true, false, List.of("rollout_read_failed"));
        }

        List<String> warnings = new ArrayList<>();
        if (malformedLines > 0) {
            warnings.add("malformed_rollout_lines");
        }
        if (events.isEmpty()) {
            warnings.add("token_usage_not_available");
        }
        return new CacheExtractionResult(sessionId, events, malformedLines, true, true, warnings);
    }

    private Optional<TokenUsage> tokenUsage(JsonNode root) {
        JsonNode info = root.path("payload").path("info").path("total_token_usage");
        if (info.isMissingNode() || info.isNull()) {
            info = root.path("payload").path("total_token_usage");
        }
        if (info.isMissingNode() || info.isNull()) {
            info = root.path("total_token_usage");
        }
        if (info.isMissingNode() || info.isNull()) {
            return Optional.empty();
        }

        long inputTokens = info.path("input_tokens").asLong(0);
        long cachedInputTokens = info.path("cached_input_tokens").asLong(0);
        long outputTokens = info.path("output_tokens").asLong(0);
        long reasoningOutputTokens = info.path("reasoning_output_tokens").asLong(0);
        long totalTokens = info.path("total_tokens").asLong(inputTokens + outputTokens + reasoningOutputTokens);
        return Optional.of(new TokenUsage(inputTokens, cachedInputTokens, outputTokens, reasoningOutputTokens, totalTokens));
    }

    private CacheTokenEvent event(String sessionId, int lineNumber, TokenUsage usage) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("lineNumber", lineNumber);
        evidence.put("source", "total_token_usage");
        evidence.put("rawContentReturned", false);
        return new CacheTokenEvent(sessionId, lineNumber, usage, evidence);
    }
}
