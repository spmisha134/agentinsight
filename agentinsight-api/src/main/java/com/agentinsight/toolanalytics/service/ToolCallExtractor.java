package com.agentinsight.toolanalytics.service;

import com.agentinsight.toolanalytics.model.NormalizedToolCall;
import com.agentinsight.toolanalytics.model.ToolExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ToolCallExtractor {
    private static final int MAX_LABEL_LENGTH = 80;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolExtractionResult extract(String sessionId, String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return new ToolExtractionResult(sessionId, List.of(), 0, false, false, List.of("rollout_path_missing"));
        }
        Path path;
        try {
            path = Path.of(rolloutPath);
        } catch (Exception e) {
            return new ToolExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_path_invalid"));
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return new ToolExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_file_not_readable"));
        }

        List<NormalizedToolCall> calls = new ArrayList<>();
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
                    normalize(sessionId, root, lineNumber).ifPresent(calls::add);
                } catch (Exception e) {
                    malformedLines++;
                }
            }
        } catch (Exception e) {
            return new ToolExtractionResult(sessionId, calls, malformedLines, true, false, List.of("rollout_read_failed"));
        }

        List<String> warnings = malformedLines > 0 ? List.of("malformed_rollout_lines") : List.of();
        return new ToolExtractionResult(sessionId, calls, malformedLines, true, true, warnings);
    }

    private Optional<NormalizedToolCall> normalize(String sessionId, JsonNode root, int lineNumber) {
        JsonNode payload = root.path("payload");
        String eventType = firstText(root, "type", "event_type").orElse("");
        String toolName = firstText(root, "tool_name", "toolName", "name")
            .or(() -> firstText(payload, "tool_name", "toolName", "name"))
            .or(() -> firstText(root.path("tool_call"), "name", "tool_name"))
            .or(() -> firstText(payload.path("tool_call"), "name", "tool_name"))
            .orElse("");
        String lowerEvent = eventType.toLowerCase(Locale.ROOT);
        if (toolName.isBlank() && !lowerEvent.contains("tool") && !hasToolFields(root) && !hasToolFields(payload)) {
            return Optional.empty();
        }

        JsonNode arguments = firstNode(root, "arguments", "arguments_json", "input")
            .or(() -> firstNode(payload, "arguments", "arguments_json", "input"))
            .orElse(null);
        JsonNode output = firstNode(root, "result", "result_preview", "output", "error")
            .or(() -> firstNode(payload, "result", "result_preview", "output", "error"))
            .orElse(null);

        String safeToolName = safeLabel(toolName.isBlank() ? inferToolName(eventType) : toolName);
        String status = status(root, payload, eventType);
        String id = firstText(root, "tool_call_id", "call_id", "id")
            .or(() -> firstText(payload, "tool_call_id", "call_id", "id"))
            .orElse(null);
        String toolType = firstText(root, "tool_type", "toolType")
            .or(() -> firstText(payload, "tool_type", "toolType"))
            .orElse(toolType(safeToolName));
        String argumentText = nodeText(arguments);
        String outputText = nodeText(output);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("lineNumber", lineNumber);
        evidence.put("eventType", eventType.isBlank() ? "unknown" : eventType);
        evidence.put("statusSource", status);
        evidence.put("hasArguments", !argumentText.isBlank());
        evidence.put("hasOutput", !outputText.isBlank());

        return Optional.of(new NormalizedToolCall(
            sessionId,
            lineNumber,
            id,
            safeToolName,
            toolType,
            status,
            eventTimeMs(root, payload),
            byteSize(argumentText),
            byteSize(outputText),
            argumentText.isBlank() ? null : sha256(argumentText),
            outputText.isBlank() ? null : sha256(outputText),
            evidence
        ));
    }

    private boolean hasToolFields(JsonNode node) {
        return node.has("tool_call") || node.has("tool_calls") || node.has("tool_name") || node.has("toolName") || node.has("tool_call_id");
    }

    private String status(JsonNode root, JsonNode payload, String eventType) {
        String explicit = firstText(root, "status").or(() -> firstText(payload, "status")).orElse("");
        String value = (explicit.isBlank() ? eventType : explicit).toLowerCase(Locale.ROOT);
        if (value.contains("error") || value.contains("failed") || value.contains("failure")) {
            return "failed";
        }
        if (value.contains("success") || value.contains("completed") || value.contains("finish") || value.contains("result") || value.contains("output")) {
            return "succeeded";
        }
        if (value.contains("start") || value.contains("call")) {
            return "started";
        }
        return "unknown";
    }

    private Long eventTimeMs(JsonNode root, JsonNode payload) {
        for (JsonNode node : List.of(root, payload)) {
            Optional<String> text = firstText(node, "timestamp", "time", "event_time", "created_at");
            if (text.isPresent()) {
                try {
                    return Instant.parse(text.get()).toEpochMilli();
                } catch (Exception ignored) {
                    try {
                        return Long.parseLong(text.get());
                    } catch (Exception ignoredAgain) {
                        // Continue with numeric fields below.
                    }
                }
            }
            for (String field : List.of("timestamp_ms", "created_at_ms", "time_ms")) {
                JsonNode value = node.path(field);
                if (value.isNumber()) {
                    return value.asLong();
                }
            }
        }
        return null;
    }

    private Optional<String> firstText(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isTextual() && !value.asText().isBlank()) {
                return Optional.of(value.asText());
            }
            if (value.isNumber()) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> firstNode(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    private String inferToolName(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "unknown";
        }
        String lower = eventType.toLowerCase(Locale.ROOT);
        if (lower.contains("exec") || lower.contains("command")) {
            return "exec";
        }
        if (lower.contains("search")) {
            return "search";
        }
        if (lower.contains("read") || lower.contains("open")) {
            return "read";
        }
        return "unknown";
    }

    private String toolType(String toolName) {
        String lower = toolName.toLowerCase(Locale.ROOT);
        if (lower.contains("exec") || lower.contains("shell") || lower.contains("command")) {
            return "command";
        }
        if (lower.contains("search") || lower.contains("rg")) {
            return "search";
        }
        if (lower.contains("read") || lower.contains("open") || lower.contains("fetch")) {
            return "read";
        }
        return "unknown";
    }

    private String safeLabel(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_LABEL_LENGTH ? normalized : normalized.substring(0, MAX_LABEL_LENGTH);
    }

    private int byteSize(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash tool payload", e);
        }
    }
}
