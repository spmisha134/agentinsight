package com.agentinsight.agentsessionreplay.service;

import com.agentinsight.agentsessionreplay.model.ReplayExtractionResult;
import com.agentinsight.agentsessionreplay.model.ReplayTimelineEvent;
import com.agentinsight.cost.model.CostBreakdown;
import com.agentinsight.cost.service.CostCalculator;
import com.agentinsight.cost.service.PricingService;
import com.agentinsight.session.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.math.BigDecimal;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ReplayTimelineBuilder {
    private static final int MAX_PREVIEW = 180;
    private static final Pattern FILE_PATH = Pattern.compile("(?<![A-Za-z0-9_.-])(?:\\.\\./)?(?:[A-Za-z0-9_.-]+/)+(?:[A-Za-z0-9_.-]+)\\b|\\b[A-Za-z0-9_.-]+\\.(?:java|kt|ts|tsx|js|jsx|py|md|yml|yaml|json|sql|gradle)\\b");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CostCalculator costCalculator;
    private final PricingService pricingService;

    public ReplayTimelineBuilder(CostCalculator costCalculator, PricingService pricingService) {
        this.costCalculator = costCalculator;
        this.pricingService = pricingService;
    }

    public ReplayExtractionResult build(String sessionId, String rolloutPath, String repositoryId, String branch, String model, ReplayRedactionOptions redaction) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return new ReplayExtractionResult(sessionId, List.of(), 0, false, false, List.of("rollout_path_missing"));
        }
        Path path;
        try {
            path = Path.of(rolloutPath);
        } catch (Exception e) {
            return new ReplayExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_path_invalid"));
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return new ReplayExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_file_not_readable"));
        }

        List<ReplayTimelineEvent> events = new ArrayList<>();
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
                    normalize(sessionId, root, lineNumber, repositoryId, branch, model, redaction).ifPresent(events::add);
                } catch (Exception e) {
                    malformedLines++;
                }
            }
        } catch (Exception e) {
            return new ReplayExtractionResult(sessionId, ordered(events), malformedLines, true, false, List.of("rollout_read_failed"));
        }

        List<String> warnings = new ArrayList<>();
        if (malformedLines > 0) {
            warnings.add("malformed_rollout_lines");
        }
        if (events.isEmpty()) {
            warnings.add("replay_events_not_available");
        }
        return new ReplayExtractionResult(sessionId, ordered(events), malformedLines, true, true, warnings);
    }

    private Optional<ReplayTimelineEvent> normalize(String sessionId, JsonNode root, int lineNumber, String repositoryId, String branch, String model, ReplayRedactionOptions redaction) {
        JsonNode payload = root.path("payload");
        String sourceType = firstText(root, "type", "event_type").orElse("unknown");
        String role = role(root, payload);
        Optional<TokenUsage> usage = tokenUsage(root);
        String toolName = toolName(root, payload);
        String command = command(root, payload).orElse(null);
        String text = text(root, payload).orElse("");
        String eventType = canonicalType(sourceType, role, toolName, command, usage, text);
        if ("unknown".equals(eventType)) {
            return Optional.empty();
        }

        String actor = actor(eventType, role);
        boolean textual = "user_message".equals(eventType) || "assistant_message".equals(eventType);
        boolean output = "assistant_message".equals(eventType) || ("tool_call".equals(eventType) && !text.isBlank());
        boolean redacted = shouldRedact(textual, output, redaction);
        String contentPreview = redacted ? redactedLabel(eventType) : preview(text);
        String filePath = filePath(command == null ? text : command).orElse(null);
        String safeFilePath = redaction.showFilePaths() ? filePath : filePath == null ? null : "<redacted-path>";
        String commandPreview = command == null ? null : preview(command);
        TokenUsage tokenUsage = usage.orElse(null);
        BigDecimal estimatedCost = tokenUsage == null ? null : costCalculator.calculate(tokenUsage, pricingService.pricingFor(model)).totalCost();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("lineNumber", lineNumber);
        evidence.put("sourceType", sourceType);
        evidence.put("rawRolloutReturned", false);
        evidence.put("contentReturned", !redacted);
        evidence.put("filePathRedacted", filePath != null && !redaction.showFilePaths());

        return Optional.of(new ReplayTimelineEvent(
            eventId(sessionId, lineNumber, eventType),
            sessionId,
            lineNumber,
            lineNumber,
            eventType,
            sourceType,
            actor,
            title(eventType, toolName, command, tokenUsage),
            contentPreview,
            text.isBlank() ? null : sha256(text),
            redacted,
            eventTimeMs(root, payload),
            status(root, payload, sourceType),
            toolName.isBlank() ? null : toolName,
            commandPreview,
            repositoryId,
            branch,
            safeFilePath,
            model,
            tokenUsage == null ? null : tokenUsage.inputTokens(),
            tokenUsage == null ? null : tokenUsage.cachedInputTokens(),
            tokenUsage == null ? null : tokenUsage.outputTokens() + tokenUsage.reasoningOutputTokens(),
            tokenUsage == null ? null : tokenUsage.totalTokens(),
            estimatedCost,
            evidence
        ));
    }

    private List<ReplayTimelineEvent> ordered(List<ReplayTimelineEvent> events) {
        List<ReplayTimelineEvent> sorted = events.stream()
            .sorted(Comparator
                .comparing((ReplayTimelineEvent event) -> event.eventTimeMs() == null ? Long.MAX_VALUE : event.eventTimeMs())
                .thenComparingInt(ReplayTimelineEvent::lineNumber))
            .toList();
        List<ReplayTimelineEvent> resequenced = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            ReplayTimelineEvent event = sorted.get(index);
            resequenced.add(new ReplayTimelineEvent(
                event.id(), event.sessionId(), index + 1, event.lineNumber(), event.eventType(), event.sourceType(), event.actor(),
                event.title(), event.contentPreview(), event.contentHash(), event.redacted(), event.eventTimeMs(), event.status(),
                event.toolName(), event.commandPreview(), event.repositoryId(), event.branch(), event.filePath(), event.model(),
                event.inputTokens(), event.cachedInputTokens(), event.outputTokens(), event.totalTokens(), event.estimatedCost(), event.evidence()
            ));
        }
        return resequenced;
    }

    private String canonicalType(String sourceType, String role, String toolName, String command, Optional<TokenUsage> usage, String text) {
        String lower = sourceType.toLowerCase(Locale.ROOT);
        if (usage.isPresent()) {
            return "token_usage";
        }
        if (command != null && !command.isBlank()) {
            return "command";
        }
        if (!toolName.isBlank() || lower.contains("tool")) {
            return "tool_call";
        }
        if ("user".equals(role)) {
            return "user_message";
        }
        if ("assistant".equals(role)) {
            return "assistant_message";
        }
        if (filePath(text).isPresent()) {
            return "file_activity";
        }
        if (lower.contains("model")) {
            return "model_change";
        }
        return "unknown";
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
        long input = info.path("input_tokens").asLong(0);
        long cached = info.path("cached_input_tokens").asLong(0);
        long output = info.path("output_tokens").asLong(0);
        long reasoning = info.path("reasoning_output_tokens").asLong(0);
        long total = info.path("total_tokens").asLong(input + output + reasoning);
        return Optional.of(new TokenUsage(input, cached, output, reasoning, total));
    }

    private Optional<String> text(JsonNode root, JsonNode payload) {
        List<String> values = new ArrayList<>();
        collectText(payload, values);
        if (values.isEmpty()) {
            collectText(root.path("message"), values);
        }
        if (values.isEmpty()) {
            collectText(root.path("item"), values);
        }
        String joined = String.join(" ", values).replaceAll("\\s+", " ").trim();
        return joined.isBlank() ? Optional.empty() : Optional.of(joined);
    }

    private void collectText(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            values.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectText(child, values));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (String field : List.of("content", "text", "message", "result_preview", "output", "error")) {
            collectText(node.path(field), values);
        }
    }

    private Optional<String> command(JsonNode root, JsonNode payload) {
        return firstText(root, "cmd", "command")
            .or(() -> firstText(payload, "cmd", "command"))
            .or(() -> firstText(root.path("arguments"), "cmd", "command", "shell_command"))
            .or(() -> firstText(payload.path("arguments"), "cmd", "command", "shell_command"))
            .or(() -> firstText(payload.path("input"), "cmd", "command", "shell_command"));
    }

    private String role(JsonNode root, JsonNode payload) {
        return firstText(root, "role")
            .or(() -> firstText(payload, "role"))
            .or(() -> firstText(root.path("message"), "role"))
            .or(() -> firstText(root.path("item"), "role"))
            .map(value -> value.toLowerCase(Locale.ROOT))
            .orElse("unknown");
    }

    private String toolName(JsonNode root, JsonNode payload) {
        return firstText(root, "tool_name", "toolName", "name")
            .or(() -> firstText(payload, "tool_name", "toolName", "name"))
            .or(() -> firstText(root.path("tool_call"), "name", "tool_name"))
            .or(() -> firstText(payload.path("tool_call"), "name", "tool_name"))
            .orElse("");
    }

    private String status(JsonNode root, JsonNode payload, String sourceType) {
        String value = firstText(root, "status").or(() -> firstText(payload, "status")).orElse(sourceType).toLowerCase(Locale.ROOT);
        if (value.contains("error") || value.contains("fail")) {
            return "failed";
        }
        if (value.contains("success") || value.contains("complete") || value.contains("result") || value.contains("output")) {
            return "succeeded";
        }
        if (value.contains("start") || value.contains("call")) {
            return "started";
        }
        return "unknown";
    }

    private String actor(String eventType, String role) {
        if (!"unknown".equals(role)) {
            return role;
        }
        if ("tool_call".equals(eventType) || "command".equals(eventType)) {
            return "tool";
        }
        if ("token_usage".equals(eventType)) {
            return "system";
        }
        return "unknown";
    }

    private String title(String eventType, String toolName, String command, TokenUsage tokenUsage) {
        return switch (eventType) {
            case "user_message" -> "User prompt";
            case "assistant_message" -> "Assistant response";
            case "command" -> command == null ? "Shell command" : "Command: " + preview(command);
            case "tool_call" -> toolName == null || toolName.isBlank() ? "Tool call" : "Tool: " + toolName;
            case "token_usage" -> tokenUsage == null ? "Token usage" : "Token usage: " + tokenUsage.totalTokens();
            case "file_activity" -> "File activity";
            case "model_change" -> "Model change";
            default -> "Replay event";
        };
    }

    private boolean shouldRedact(boolean textual, boolean output, ReplayRedactionOptions redaction) {
        if (textual && !redaction.showPrompts()) {
            return true;
        }
        return output && !redaction.showOutputs();
    }

    private String redactedLabel(String eventType) {
        return switch (eventType) {
            case "user_message" -> "<prompt redacted>";
            case "assistant_message" -> "<assistant output redacted>";
            default -> "<content redacted>";
        };
    }

    private Optional<String> filePath(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = FILE_PATH.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
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

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim()
            .replaceAll("(?i)(password|token|secret|api[_-]?key)=\\S+", "$1=<redacted>");
        return normalized.length() <= MAX_PREVIEW ? normalized : normalized.substring(0, MAX_PREVIEW);
    }

    private String eventId(String sessionId, int lineNumber, String eventType) {
        return sha256(sessionId + "|" + lineNumber + "|" + eventType).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash replay event", e);
        }
    }
}
