package com.agentinsight.contextmemory.service;

import com.agentinsight.contextmemory.model.ContextExtractionResult;
import com.agentinsight.contextmemory.model.ContextSegment;
import com.agentinsight.contextmemory.model.MemoryReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ContextExtractor {
    private static final int TOKEN_CHARS = 4;
    private static final Pattern AGENTS = Pattern.compile("\\bAGENTS\\.md\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL = Pattern.compile("\\b([A-Za-z0-9_.-]+/)?SKILL\\.md\\b|\\bskills?/([A-Za-z0-9_.-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_DOC = Pattern.compile(
        "(?<![A-Za-z0-9_.-])((?:\\.\\./)?application-build-resources/AgentInsight/(?:specs|docs)/[A-Za-z0-9_./-]+|(?:specs|docs)/[A-Za-z0-9_./-]+|README\\.md)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContextExtractionResult extract(String sessionId, String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return new ContextExtractionResult(sessionId, List.of(), 0, false, false, List.of("rollout_path_missing"));
        }
        Path path;
        try {
            path = Path.of(rolloutPath);
        } catch (Exception e) {
            return new ContextExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_path_invalid"));
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return new ContextExtractionResult(sessionId, List.of(), 0, true, false, List.of("rollout_file_not_readable"));
        }

        List<ContextSegment> segments = new ArrayList<>();
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
                    Optional<String> content = extractText(root);
                    if (content.isPresent() && !content.get().isBlank()) {
                        String text = normalizeWhitespace(content.get());
                        segments.add(new ContextSegment(
                            sessionId,
                            lineNumber,
                            role(root),
                            source(root),
                            sha256(text.toLowerCase(Locale.ROOT)),
                            text.length(),
                            estimateTokens(text),
                            detectMemoryReferences(text, lineNumber)
                        ));
                    }
                } catch (Exception e) {
                    malformedLines++;
                }
            }
        } catch (Exception e) {
            return new ContextExtractionResult(sessionId, segments, malformedLines, true, false, List.of("rollout_read_failed"));
        }

        List<String> warnings = malformedLines > 0 ? List.of("malformed_rollout_lines") : List.of();
        return new ContextExtractionResult(sessionId, segments, malformedLines, true, true, warnings);
    }

    private Optional<String> extractText(JsonNode root) {
        List<String> values = new ArrayList<>();
        collectText(root.path("payload"), values);
        if (values.isEmpty()) {
            collectText(root.path("item"), values);
        }
        if (values.isEmpty()) {
            collectText(root.path("message"), values);
        }
        if (values.isEmpty()) {
            collectText(root, values);
        }
        String joined = normalizeWhitespace(String.join(" ", values));
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
            for (JsonNode child : node) {
                collectText(child, values);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (String field : List.of("content", "text", "message", "input", "arguments", "result_preview")) {
            JsonNode child = node.get(field);
            if (child != null) {
                collectText(child, values);
            }
        }
    }

    private List<MemoryReference> detectMemoryReferences(String text, int lineNumber) {
        Set<MemoryReference> references = new LinkedHashSet<>();
        addMatches(references, AGENTS.matcher(text), "repo_instructions", lineNumber);
        addMatches(references, PROJECT_DOC.matcher(text), "project_doc", lineNumber);
        addMatches(references, SKILL.matcher(text), "skill", lineNumber);
        return List.copyOf(references);
    }

    private void addMatches(Set<MemoryReference> references, Matcher matcher, String type, int lineNumber) {
        while (matcher.find()) {
            references.add(new MemoryReference(type, matcher.group(), lineNumber, "medium"));
        }
    }

    private String role(JsonNode root) {
        for (JsonNode candidate : List.of(root.path("role"), root.path("payload").path("role"), root.path("item").path("role"), root.path("message").path("role"))) {
            if (candidate.isTextual() && !candidate.asText().isBlank()) {
                return candidate.asText().toLowerCase(Locale.ROOT);
            }
        }
        return "unknown";
    }

    private String source(JsonNode root) {
        String type = root.path("type").asText(root.path("event_type").asText("unknown"));
        if (type.isBlank()) {
            return "unknown";
        }
        return type;
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private long estimateTokens(String value) {
        return Math.max(1L, (long) Math.ceil(value.length() / (double) TOKEN_CHARS));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash context segment", e);
        }
    }
}
