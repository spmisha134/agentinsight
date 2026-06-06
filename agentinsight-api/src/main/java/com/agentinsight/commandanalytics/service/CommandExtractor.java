package com.agentinsight.commandanalytics.service;

import com.agentinsight.commandanalytics.model.CommandEvent;
import com.agentinsight.commandanalytics.model.CommandExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CommandExtractor {
    private static final int MAX_COMMAND_PREVIEW = 120;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CommandClassifier classifier;

    public CommandExtractor(CommandClassifier classifier) {
        this.classifier = classifier;
    }

    public CommandExtractionResult extract(String sessionId, String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return new CommandExtractionResult(sessionId, List.of(), 0, false, false, List.of("rollout_path_missing"));
        }
        Path path;
        try {
            path = Path.of(rolloutPath);
        } catch (Exception e) {
            return new CommandExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_path_invalid"));
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return new CommandExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_file_not_readable"));
        }

        Map<String, PartialCommand> byId = new LinkedHashMap<>();
        List<CommandEvent> commandsWithoutIds = new ArrayList<>();
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
                    normalize(sessionId, root, lineNumber, byId, commandsWithoutIds);
                } catch (Exception e) {
                    malformedLines++;
                }
            }
        } catch (Exception e) {
            return new CommandExtractionResult(sessionId, materialize(byId, commandsWithoutIds), malformedLines, true, false, List.of("rollout_read_failed"));
        }

        List<String> warnings = malformedLines > 0 ? List.of("malformed_rollout_lines") : List.of();
        return new CommandExtractionResult(sessionId, materialize(byId, commandsWithoutIds), malformedLines, true, true, warnings);
    }

    private void normalize(String sessionId, JsonNode root, int lineNumber, Map<String, PartialCommand> byId, List<CommandEvent> commandsWithoutIds) {
        JsonNode payload = root.path("payload");
        String eventType = firstText(root, "type", "event_type").orElse("");
        String toolName = firstText(root, "tool_name", "toolName", "name")
            .or(() -> firstText(payload, "tool_name", "toolName", "name"))
            .or(() -> firstText(root.path("tool_call"), "name", "tool_name"))
            .or(() -> firstText(payload.path("tool_call"), "name", "tool_name"))
            .orElse("");
        JsonNode arguments = firstNode(root, "arguments", "arguments_json", "input")
            .or(() -> firstNode(payload, "arguments", "arguments_json", "input"))
            .orElse(null);
        JsonNode result = firstNode(root, "result", "result_preview", "output", "error")
            .or(() -> firstNode(payload, "result", "result_preview", "output", "error"))
            .orElse(null);

        Optional<String> command = command(root, payload, arguments);
        boolean commandTool = isCommandTool(toolName, eventType, root, payload, arguments);
        if (!commandTool && command.isEmpty()) {
            return;
        }

        String id = firstText(root, "tool_call_id", "call_id", "id")
            .or(() -> firstText(payload, "tool_call_id", "call_id", "id"))
            .orElse(null);
        String status = status(root, payload, eventType);
        Long eventTimeMs = eventTimeMs(root, payload);
        Integer exitCode = exitCode(root, payload, result).orElse(null);
        int stdoutBytes = outputBytes(result, "stdout");
        int stderrBytes = outputBytes(result, "stderr");
        String cwd = firstText(root, "cwd", "working_directory")
            .or(() -> firstText(payload, "cwd", "working_directory"))
            .or(() -> textField(arguments, "cwd", "working_directory"))
            .orElse(null);

        if (id == null || id.isBlank()) {
            command.ifPresent(value -> commandsWithoutIds.add(build(
                sessionId, lineNumber, null, value, cwd, status, exitCode,
                "failed".equals(status) || "succeeded".equals(status) ? null : eventTimeMs,
                "failed".equals(status) || "succeeded".equals(status) ? eventTimeMs : null,
                null, stdoutBytes, stderrBytes, eventType
            )));
            return;
        }

        PartialCommand partial = byId.computeIfAbsent(id, key -> new PartialCommand(sessionId, id));
        partial.lineNumber = Math.min(partial.lineNumber, lineNumber);
        partial.eventType = partial.eventType.isBlank() ? eventType : partial.eventType;
        command.ifPresent(value -> partial.command = value);
        if (cwd != null && !cwd.isBlank()) {
            partial.cwd = cwd;
        }
        if ("started".equals(status) && eventTimeMs != null) {
            partial.startedAtMs = min(partial.startedAtMs, eventTimeMs);
        }
        if (("succeeded".equals(status) || "failed".equals(status)) && eventTimeMs != null) {
            partial.completedAtMs = max(partial.completedAtMs, eventTimeMs);
        }
        if (exitCode != null) {
            partial.exitCode = exitCode;
            partial.status = exitCode == 0 ? "succeeded" : "failed";
        } else if (!"unknown".equals(status)) {
            partial.status = status;
        }
        partial.stdoutSizeBytes += stdoutBytes;
        partial.stderrSizeBytes += stderrBytes;
    }

    private List<CommandEvent> materialize(Map<String, PartialCommand> byId, List<CommandEvent> commandsWithoutIds) {
        List<CommandEvent> events = new ArrayList<>(commandsWithoutIds);
        byId.values().stream()
            .filter(partial -> partial.command != null && !partial.command.isBlank())
            .map(PartialCommand::toEvent)
            .forEach(events::add);
        return events.stream()
            .sorted(Comparator.comparingInt(CommandEvent::lineNumber))
            .toList();
    }

    private CommandEvent build(
        String sessionId,
        int lineNumber,
        String toolCallId,
        String command,
        String cwd,
        String status,
        Integer exitCode,
        Long startedAtMs,
        Long completedAtMs,
        Long durationMs,
        int stdoutBytes,
        int stderrBytes,
        String eventType
    ) {
        Long resolvedDuration = durationMs;
        if (resolvedDuration == null && startedAtMs != null && completedAtMs != null && completedAtMs >= startedAtMs) {
            resolvedDuration = completedAtMs - startedAtMs;
        }
        String riskLevel = classifier.riskLevel(command);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("lineNumber", lineNumber);
        evidence.put("eventType", eventType == null || eventType.isBlank() ? "unknown" : eventType);
        evidence.put("rawOutputReturned", false);
        evidence.put("commandHash", sha256(command));
        evidence.put("durationSource", resolvedDuration == null ? "not_available" : "tool_lifecycle");
        return new CommandEvent(
            sessionId,
            lineNumber,
            toolCallId,
            commandPreview(command),
            sha256(command),
            classifier.executable(command),
            cwd,
            classifier.classify(command),
            status == null || status.isBlank() ? "unknown" : status,
            exitCode,
            startedAtMs,
            completedAtMs,
            resolvedDuration,
            stdoutBytes,
            stderrBytes,
            riskLevel,
            classifier.riskReason(command),
            evidence
        );
    }

    private Optional<String> command(JsonNode root, JsonNode payload, JsonNode arguments) {
        return firstText(root, "cmd", "command")
            .or(() -> firstText(payload, "cmd", "command"))
            .or(() -> textField(arguments, "cmd", "command", "shell_command"));
    }

    private boolean isCommandTool(JsonNode node) {
        return textField(node, "cmd", "command", "shell_command").isPresent();
    }

    private boolean isCommandTool(String toolName, String eventType, JsonNode root, JsonNode payload, JsonNode arguments) {
        String label = (toolName + " " + eventType).toLowerCase(Locale.ROOT);
        return label.contains("exec") || label.contains("shell") || label.contains("command")
            || isCommandTool(root) || isCommandTool(payload) || isCommandTool(arguments);
    }

    private String status(JsonNode root, JsonNode payload, String eventType) {
        Optional<Integer> exitCode = exitCode(root, payload, null);
        if (exitCode.isPresent()) {
            return exitCode.get() == 0 ? "succeeded" : "failed";
        }
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

    private Optional<Integer> exitCode(JsonNode root, JsonNode payload, JsonNode result) {
        return intField(root, "exit_code", "exitCode", "code")
            .or(() -> intField(payload, "exit_code", "exitCode", "code"))
            .or(() -> intField(result, "exit_code", "exitCode", "code"));
    }

    private int outputBytes(JsonNode result, String streamName) {
        if (result == null || result.isMissingNode() || result.isNull()) {
            return 0;
        }
        Optional<String> stream = textField(result, streamName, streamName + "_preview");
        if (stream.isPresent()) {
            return byteSize(stream.get());
        }
        if ("stdout".equals(streamName) && result.isTextual()) {
            return byteSize(result.asText());
        }
        if ("stderr".equals(streamName)) {
            return byteSize(textField(result, "error", "stderr").orElse(""));
        }
        return 0;
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

    private Optional<String> textField(JsonNode node, String... names) {
        return firstText(node, names);
    }

    private Optional<Integer> intField(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isInt() || value.isLong()) {
                return Optional.of(value.asInt());
            }
            if (value.isTextual()) {
                try {
                    return Optional.of(Integer.parseInt(value.asText()));
                } catch (Exception ignored) {
                    // Continue to next field.
                }
            }
        }
        return Optional.empty();
    }

    private String commandPreview(String command) {
        String normalized = command.replaceAll("\\s+", " ").trim()
            .replaceAll("(?i)(password|token|secret|api[_-]?key)=\\S+", "$1=<redacted>");
        return normalized.length() <= MAX_COMMAND_PREVIEW ? normalized : normalized.substring(0, MAX_COMMAND_PREVIEW);
    }

    private int byteSize(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash command", e);
        }
    }

    private Long min(Long current, Long candidate) {
        return current == null || candidate < current ? candidate : current;
    }

    private Long max(Long current, Long candidate) {
        return current == null || candidate > current ? candidate : current;
    }

    private class PartialCommand {
        private final String sessionId;
        private final String toolCallId;
        private int lineNumber = Integer.MAX_VALUE;
        private String eventType = "";
        private String command;
        private String cwd;
        private String status = "unknown";
        private Integer exitCode;
        private Long startedAtMs;
        private Long completedAtMs;
        private int stdoutSizeBytes;
        private int stderrSizeBytes;

        private PartialCommand(String sessionId, String toolCallId) {
            this.sessionId = sessionId;
            this.toolCallId = toolCallId;
        }

        private CommandEvent toEvent() {
            return build(sessionId, lineNumber, toolCallId, command, cwd, status, exitCode, startedAtMs, completedAtMs, null, stdoutSizeBytes, stderrSizeBytes, eventType);
        }
    }
}
