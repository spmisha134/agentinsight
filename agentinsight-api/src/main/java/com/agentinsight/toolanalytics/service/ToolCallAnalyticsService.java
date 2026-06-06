package com.agentinsight.toolanalytics.service;

import com.agentinsight.source.sqlite.CodexStateRepository;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import com.agentinsight.toolanalytics.model.NormalizedToolCall;
import com.agentinsight.toolanalytics.model.ToolAnalyticsSummary;
import com.agentinsight.toolanalytics.model.ToolDailyAnalytics;
import com.agentinsight.toolanalytics.model.ToolExtractionResult;
import com.agentinsight.toolanalytics.model.ToolMetricSummary;
import com.agentinsight.toolanalytics.model.ToolSessionAnalytics;
import com.agentinsight.toolanalytics.model.ToolUsageStat;
import com.agentinsight.toolanalytics.model.ToolWarning;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ToolCallAnalyticsService {
    private final CodexStateRepository repository;
    private final ToolCallExtractor extractor;
    private final ToolMetricCalculator calculator;

    public ToolCallAnalyticsService(CodexStateRepository repository, ToolCallExtractor extractor, ToolMetricCalculator calculator) {
        this.repository = repository;
        this.extractor = extractor;
        this.calculator = calculator;
    }

    public ToolAnalyticsSummary summary(String repositoryId, String model, String day, String status, String toolType) {
        List<ToolSessionAnalytics> sessions = filteredSessions(repositoryId, model, day, status, toolType);
        List<ToolUsageStat> tools = aggregateTools(sessions);
        int totalCalls = sessions.stream().mapToInt(session -> session.metrics().totalToolCalls()).sum();
        int failedCalls = sessions.stream().mapToInt(session -> session.metrics().failedToolCalls()).sum();
        int retryCount = sessions.stream().mapToInt(session -> session.metrics().retryCount()).sum();
        long outputBytes = sessions.stream().mapToLong(session -> session.metrics().totalOutputBytes()).sum();
        Long averageLatency = averageLatency(sessions);
        return new ToolAnalyticsSummary(
            Instant.now(),
            dataCompleteness(sessions),
            sessions.size(),
            totalCalls,
            failedCalls,
            retryCount,
            outputBytes,
            averageLatency,
            ratio(failedCalls, totalCalls),
            ratio(retryCount, totalCalls),
            sessions.stream().flatMap(session -> session.warnings().stream()).map(ToolWarning::code).distinct().toList(),
            tools,
            sessions
        );
    }

    public List<ToolDailyAnalytics> daily(String repositoryId, String model, String status, String toolType) {
        return filteredSessions(repositoryId, model, null, status, toolType).stream()
            .collect(Collectors.groupingBy(session -> day(session.updatedAtMs()), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> daily(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(ToolDailyAnalytics::day))
            .toList();
    }

    public Optional<ToolSessionAnalytics> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return analytics().stream().filter(session -> session.sessionId().equals(sessionId)).findFirst();
    }

    public Optional<ToolAnalyticsSummary> repositorySummary(String repositoryId) {
        ToolAnalyticsSummary summary = summary(repositoryId, null, null, null, null);
        return summary.totalSessions() == 0 ? Optional.empty() : Optional.of(summary);
    }

    public List<ToolWarning> warnings(String repositoryId, String model, String day, String status, String toolType) {
        return filteredSessions(repositoryId, model, day, status, toolType).stream()
            .flatMap(session -> session.warnings().stream())
            .toList();
    }

    private List<ToolSessionAnalytics> filteredSessions(String repositoryId, String model, String day, String status, String toolType) {
        return analytics().stream()
            .filter(session -> repositoryId == null || repositoryId.isBlank() || repositoryId.equals(session.repositoryId()))
            .filter(session -> model == null || model.isBlank() || model.equals(session.model()))
            .filter(session -> day == null || day.isBlank() || day.equals(day(session.updatedAtMs())))
            .filter(session -> status == null || status.isBlank() || status.equals(session.dataCompleteness()))
            .filter(session -> toolType == null || toolType.isBlank() || session.tools().stream().anyMatch(tool -> toolType.equals(tool.toolType())))
            .toList();
    }

    private List<ToolSessionAnalytics> analytics() {
        return repository.findThreads().stream()
            .map(this::analyze)
            .sorted(Comparator.comparingLong(ToolSessionAnalytics::updatedAtMs).reversed())
            .toList();
    }

    private ToolSessionAnalytics analyze(RawThread thread) {
        ToolExtractionResult extraction = extractor.extract(thread.id(), thread.rolloutPath());
        List<NormalizedToolCall> calls = extraction.toolCalls();
        ToolMetricSummary metrics = calculator.calculate(calls);
        List<ToolWarning> warnings = new ArrayList<>(calculator.warnings(calls));
        warnings.addAll(extractionWarnings(extraction));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
        evidence.put("rolloutReadable", extraction.rolloutReadable());
        evidence.put("malformedLines", extraction.malformedLines());
        evidence.put("toolLineNumbers", calls.stream().map(NormalizedToolCall::lineNumber).limit(100).toList());
        evidence.put("rawContentReturned", false);

        return new ToolSessionAnalytics(
            thread.id(),
            null,
            thread.cwd(),
            repositoryId(thread),
            blankToUnknown(thread.model()),
            thread.createdAtMs(),
            thread.updatedAtMs(),
            metrics,
            dataCompleteness(extraction),
            calculator.toolStats(calls),
            warnings.stream().limit(25).toList(),
            evidence
        );
    }

    private List<ToolWarning> extractionWarnings(ToolExtractionResult extraction) {
        List<ToolWarning> warnings = new ArrayList<>();
        for (String warning : extraction.warnings()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("malformedLines", extraction.malformedLines());
            evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
            evidence.put("rolloutReadable", extraction.rolloutReadable());
            warnings.add(new ToolWarning("warning", warning, warning.replace('_', ' '), evidence));
        }
        return warnings;
    }

    private List<ToolUsageStat> aggregateTools(List<ToolSessionAnalytics> sessions) {
        Map<String, List<ToolUsageStat>> grouped = sessions.stream()
            .flatMap(session -> session.tools().stream())
            .collect(Collectors.groupingBy(tool -> tool.toolName() + "\u0000" + tool.toolType(), LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
            .map(entry -> aggregateTool(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(ToolUsageStat::calls).reversed())
            .limit(20)
            .toList();
    }

    private ToolUsageStat aggregateTool(String key, List<ToolUsageStat> tools) {
        String[] parts = key.split("\u0000", -1);
        int calls = tools.stream().mapToInt(ToolUsageStat::calls).sum();
        int failures = tools.stream().mapToInt(ToolUsageStat::failures).sum();
        long outputBytes = tools.stream().mapToLong(ToolUsageStat::outputBytes).sum();
        Long latency = averageToolLatency(tools);
        return new ToolUsageStat(parts[0], parts[1], calls, failures, ratio(failures, calls), outputBytes, latency);
    }

    private ToolDailyAnalytics daily(String day, List<ToolSessionAnalytics> sessions) {
        int calls = sessions.stream().mapToInt(session -> session.metrics().totalToolCalls()).sum();
        int failures = sessions.stream().mapToInt(session -> session.metrics().failedToolCalls()).sum();
        int retries = sessions.stream().mapToInt(session -> session.metrics().retryCount()).sum();
        return new ToolDailyAnalytics(day, sessions.size(), calls, failures, retries, ratio(failures, calls));
    }

    private Long averageLatency(List<ToolSessionAnalytics> sessions) {
        List<Long> values = sessions.stream().map(session -> session.metrics().averageLatencyMs()).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private Long averageToolLatency(List<ToolUsageStat> tools) {
        List<Long> values = tools.stream().map(ToolUsageStat::averageLatencyMs).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private String dataCompleteness(ToolExtractionResult extraction) {
        if (!extraction.rolloutPathPresent()) {
            return "not_available";
        }
        if (!extraction.rolloutReadable() || extraction.malformedLines() > 0) {
            return "partial";
        }
        return "exact";
    }

    private String dataCompleteness(List<ToolSessionAnalytics> sessions) {
        if (sessions.isEmpty()) {
            return "not_available";
        }
        if (sessions.stream().allMatch(session -> "exact".equals(session.dataCompleteness()))) {
            return "exact";
        }
        return "partial";
    }

    private String repositoryId(RawThread thread) {
        if (thread.repositoryUrl() != null && !thread.repositoryUrl().isBlank()) {
            return thread.repositoryUrl().replace("git@github.com:", "github.com/").replace(".git", "");
        }
        if (thread.cwd() == null || thread.cwd().isBlank()) {
            return "unknown";
        }
        return Path.of(thread.cwd()).getFileName().toString();
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String day(long epochMs) {
        if (epochMs <= 0L) {
            return "unknown";
        }
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC).toString();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }
}
