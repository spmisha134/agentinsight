package com.agentinsight.contextmemory.service;

import com.agentinsight.contextmemory.model.ContextAnalyticsSummary;
import com.agentinsight.contextmemory.model.ContextDailyAnalytics;
import com.agentinsight.contextmemory.model.ContextExtractionResult;
import com.agentinsight.contextmemory.model.ContextMetricSummary;
import com.agentinsight.contextmemory.model.ContextSegment;
import com.agentinsight.contextmemory.model.ContextSessionAnalytics;
import com.agentinsight.contextmemory.model.ContextWarning;
import com.agentinsight.contextmemory.model.MemoryReference;
import com.agentinsight.source.sqlite.CodexStateRepository;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
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
public class ContextMemoryAnalyticsService {
    private static final int MAX_SESSION_WARNINGS = 25;

    private final CodexStateRepository repository;
    private final ContextExtractor extractor;
    private final ContextMetricCalculator calculator;

    public ContextMemoryAnalyticsService(CodexStateRepository repository, ContextExtractor extractor, ContextMetricCalculator calculator) {
        this.repository = repository;
        this.extractor = extractor;
        this.calculator = calculator;
    }

    public ContextAnalyticsSummary summary(String repositoryId, String model, String day, String status) {
        List<ContextSessionAnalytics> sessions = filteredSessions(repositoryId, model, day, status);
        long totalTokens = sessions.stream().mapToLong(session -> session.metrics().totalContextTokens()).sum();
        long repeatedTokens = sessions.stream().mapToLong(session -> session.metrics().repeatedContextTokens()).sum();
        long cacheableTokens = sessions.stream().mapToLong(session -> session.metrics().cacheableContextTokens()).sum();
        return new ContextAnalyticsSummary(
            Instant.now(),
            dataCompleteness(sessions),
            sessions.size(),
            totalTokens,
            repeatedTokens,
            cacheableTokens,
            ratio(Math.max(0L, totalTokens - repeatedTokens), totalTokens),
            ratio(repeatedTokens, totalTokens),
            sessions.stream().flatMap(session -> session.warnings().stream()).map(ContextWarning::code).distinct().toList(),
            sessions
        );
    }

    public List<ContextDailyAnalytics> daily(String repositoryId, String model, String status) {
        return filteredSessions(repositoryId, model, null, status).stream()
            .collect(Collectors.groupingBy(session -> day(session.updatedAtMs()), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> daily(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(ContextDailyAnalytics::day))
            .toList();
    }

    public Optional<ContextSessionAnalytics> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return analytics().stream()
            .filter(session -> session.sessionId().equals(sessionId))
            .findFirst();
    }

    public Optional<ContextAnalyticsSummary> repositorySummary(String repositoryId) {
        ContextAnalyticsSummary summary = summary(repositoryId, null, null, null);
        return summary.totalSessions() == 0 ? Optional.empty() : Optional.of(summary);
    }

    public List<ContextWarning> warnings(String repositoryId, String model, String day, String status) {
        return filteredSessions(repositoryId, model, day, status).stream()
            .flatMap(session -> session.warnings().stream())
            .toList();
    }

    private List<ContextSessionAnalytics> filteredSessions(String repositoryId, String model, String day, String status) {
        return analytics().stream()
            .filter(session -> repositoryId == null || repositoryId.isBlank() || repositoryId.equals(session.repositoryId()))
            .filter(session -> model == null || model.isBlank() || model.equals(session.model()))
            .filter(session -> day == null || day.isBlank() || day.equals(day(session.updatedAtMs())))
            .filter(session -> status == null || status.isBlank() || status.equals(session.dataCompleteness()))
            .toList();
    }

    private List<ContextSessionAnalytics> analytics() {
        return repository.findThreads().stream()
            .map(this::analyze)
            .sorted(Comparator.comparingLong(ContextSessionAnalytics::updatedAtMs).reversed())
            .toList();
    }

    private ContextSessionAnalytics analyze(RawThread thread) {
        ContextExtractionResult extraction = extractor.extract(thread.id(), thread.rolloutPath());
        List<ContextSegment> segments = extraction.segments();
        ContextMetricSummary metrics = calculator.calculate(segments);
        List<MemoryReference> memoryReferences = segments.stream()
            .flatMap(segment -> segment.memoryReferences().stream())
            .distinct()
            .limit(50)
            .toList();
        List<ContextWarning> warnings = new ArrayList<>(calculator.repeatedContextWarnings(segments));
        warnings.addAll(extractionWarnings(extraction));
        List<ContextWarning> boundedWarnings = warnings.stream().limit(MAX_SESSION_WARNINGS).toList();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
        evidence.put("rolloutReadable", extraction.rolloutReadable());
        evidence.put("malformedLines", extraction.malformedLines());
        evidence.put("segmentLineNumbers", segments.stream().map(ContextSegment::lineNumber).limit(100).toList());
        evidence.put("metricSource", "rollout_text_estimate");

        return new ContextSessionAnalytics(
            thread.id(),
            null,
            thread.cwd(),
            repositoryId(thread),
            blankToUnknown(thread.model()),
            thread.createdAtMs(),
            thread.updatedAtMs(),
            segments.size(),
            memoryReferences.size(),
            metrics,
            dataCompleteness(extraction),
            memoryReferences,
            boundedWarnings,
            evidence
        );
    }

    private List<ContextWarning> extractionWarnings(ContextExtractionResult extraction) {
        List<ContextWarning> warnings = new ArrayList<>();
        for (String warning : extraction.warnings()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("malformedLines", extraction.malformedLines());
            evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
            evidence.put("rolloutReadable", extraction.rolloutReadable());
            warnings.add(new ContextWarning("warning", warning, warning.replace('_', ' '), evidence));
        }
        return warnings;
    }

    private ContextDailyAnalytics daily(String day, List<ContextSessionAnalytics> sessions) {
        long totalTokens = sessions.stream().mapToLong(session -> session.metrics().totalContextTokens()).sum();
        long repeatedTokens = sessions.stream().mapToLong(session -> session.metrics().repeatedContextTokens()).sum();
        long cacheableTokens = sessions.stream().mapToLong(session -> session.metrics().cacheableContextTokens()).sum();
        return new ContextDailyAnalytics(day, sessions.size(), totalTokens, repeatedTokens, cacheableTokens, ratio(repeatedTokens, totalTokens));
    }

    private String dataCompleteness(ContextExtractionResult extraction) {
        if (!extraction.rolloutPathPresent()) {
            return "not_available";
        }
        if (!extraction.rolloutReadable() || extraction.malformedLines() > 0) {
            return "partial";
        }
        return "exact";
    }

    private String dataCompleteness(List<ContextSessionAnalytics> sessions) {
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
