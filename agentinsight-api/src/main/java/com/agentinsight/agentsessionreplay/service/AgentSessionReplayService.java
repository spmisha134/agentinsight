package com.agentinsight.agentsessionreplay.service;

import com.agentinsight.agentsessionreplay.model.ReplayDailyAnalytics;
import com.agentinsight.agentsessionreplay.model.ReplayExtractionResult;
import com.agentinsight.agentsessionreplay.model.ReplayMetricSummary;
import com.agentinsight.agentsessionreplay.model.ReplaySession;
import com.agentinsight.agentsessionreplay.model.ReplaySummary;
import com.agentinsight.agentsessionreplay.model.ReplayTimelineEvent;
import com.agentinsight.agentsessionreplay.model.ReplayWarning;
import com.agentinsight.source.sqlite.CodexStateRepository;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import java.math.BigDecimal;
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
public class AgentSessionReplayService {
    private static final int SUMMARY_EVENT_LIMIT = 8;
    private final CodexStateRepository repository;
    private final ReplayTimelineBuilder builder;

    public AgentSessionReplayService(CodexStateRepository repository, ReplayTimelineBuilder builder) {
        this.repository = repository;
        this.builder = builder;
    }

    public ReplaySummary summary(String repositoryId, String model, String day, String status, String eventType) {
        List<ReplaySession> sessions = filteredSessions(repositoryId, model, day, status, eventType, 0, SUMMARY_EVENT_LIMIT, ReplayRedactionOptions.defaults());
        int totalEvents = sessions.stream().mapToInt(session -> session.metrics().totalEvents()).sum();
        int malformed = sessions.stream()
            .map(session -> session.evidence().get("malformedLines"))
            .filter(Integer.class::isInstance)
            .map(Integer.class::cast)
            .mapToInt(Integer::intValue)
            .sum();
        long totalTokens = sessions.stream().mapToLong(session -> session.metrics().totalTokens()).sum();
        BigDecimal estimatedCost = sessions.stream().map(session -> session.metrics().estimatedCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReplaySummary(
            Instant.now(),
            dataCompleteness(sessions),
            sessions.size(),
            totalEvents,
            (int) sessions.stream().filter(session -> session.totalEvents() > 0).count(),
            malformed,
            totalTokens,
            estimatedCost,
            sessions.stream().flatMap(session -> session.warnings().stream()).map(ReplayWarning::code).distinct().toList(),
            sessions
        );
    }

    public List<ReplayDailyAnalytics> daily(String repositoryId, String model, String status, String eventType) {
        return filteredSessions(repositoryId, model, null, status, eventType, 0, SUMMARY_EVENT_LIMIT, ReplayRedactionOptions.defaults()).stream()
            .collect(Collectors.groupingBy(session -> day(session.updatedAtMs()), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> daily(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(ReplayDailyAnalytics::day))
            .toList();
    }

    public Optional<ReplaySession> session(String sessionId, int page, int size, String eventType, ReplayRedactionOptions redaction) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return repository.findThreads().stream()
            .filter(thread -> sessionId.equals(thread.id()))
            .findFirst()
            .map(thread -> analyze(thread, eventType, page, boundedSize(size), redaction));
    }

    public Optional<ReplaySummary> repositorySummary(String repositoryId) {
        ReplaySummary summary = summary(repositoryId, null, null, null, null);
        return summary.totalSessions() == 0 ? Optional.empty() : Optional.of(summary);
    }

    public List<ReplayWarning> warnings(String repositoryId, String model, String day, String status, String eventType) {
        return filteredSessions(repositoryId, model, day, status, eventType, 0, SUMMARY_EVENT_LIMIT, ReplayRedactionOptions.defaults()).stream()
            .flatMap(session -> session.warnings().stream())
            .toList();
    }

    private List<ReplaySession> filteredSessions(String repositoryId, String model, String day, String status, String eventType, int page, int size, ReplayRedactionOptions redaction) {
        return repository.findThreads().stream()
            .map(thread -> analyze(thread, eventType, page, size, redaction))
            .filter(session -> repositoryId == null || repositoryId.isBlank() || repositoryId.equals(session.repositoryId()))
            .filter(session -> model == null || model.isBlank() || model.equals(session.model()))
            .filter(session -> day == null || day.isBlank() || day.equals(day(session.updatedAtMs())))
            .filter(session -> status == null || status.isBlank() || status.equals(session.dataCompleteness()))
            .sorted(Comparator.comparingLong(ReplaySession::updatedAtMs).reversed())
            .toList();
    }

    private ReplaySession analyze(RawThread thread, String eventType, int page, int size, ReplayRedactionOptions redaction) {
        String repositoryId = repositoryId(thread);
        ReplayExtractionResult extraction = builder.build(thread.id(), thread.rolloutPath(), repositoryId, thread.branch(), blankToUnknown(thread.model()), redaction);
        List<ReplayTimelineEvent> filtered = extraction.events().stream()
            .filter(event -> eventType == null || eventType.isBlank() || eventType.equals(event.eventType()))
            .toList();
        ReplayMetricSummary metrics = metrics(extraction.events());
        List<ReplayWarning> warnings = extractionWarnings(extraction, metrics);

        int safePage = Math.max(0, page);
        int safeSize = boundedSize(size);
        int from = Math.min(filtered.size(), safePage * safeSize);
        int to = Math.min(filtered.size(), from + safeSize);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
        evidence.put("rolloutReadable", extraction.rolloutReadable());
        evidence.put("malformedLines", extraction.malformedLines());
        evidence.put("eventLineNumbers", extraction.events().stream().map(ReplayTimelineEvent::lineNumber).limit(100).toList());
        evidence.put("rawRolloutReturned", false);

        return new ReplaySession(
            thread.id(),
            thread.title(),
            thread.cwd(),
            repositoryId,
            blankToUnknown(thread.branch()),
            blankToUnknown(thread.model()),
            thread.createdAtMs(),
            thread.updatedAtMs(),
            metrics,
            dataCompleteness(extraction),
            safePage,
            safeSize,
            filtered.size(),
            filtered.subList(from, to),
            warnings,
            evidence
        );
    }

    private ReplayMetricSummary metrics(List<ReplayTimelineEvent> events) {
        int messages = (int) events.stream().filter(event -> event.eventType().endsWith("_message")).count();
        int tools = (int) events.stream().filter(event -> "tool_call".equals(event.eventType())).count();
        int commands = (int) events.stream().filter(event -> "command".equals(event.eventType())).count();
        int files = (int) events.stream().filter(event -> "file_activity".equals(event.eventType())).count();
        int tokenEvents = (int) events.stream().filter(event -> "token_usage".equals(event.eventType())).count();
        long totalTokens = events.stream().map(ReplayTimelineEvent::totalTokens).filter(value -> value != null).mapToLong(Long::longValue).max().orElse(0L);
        long cachedTokens = events.stream().map(ReplayTimelineEvent::cachedInputTokens).filter(value -> value != null).mapToLong(Long::longValue).max().orElse(0L);
        BigDecimal cost = events.stream().map(ReplayTimelineEvent::estimatedCost).filter(value -> value != null).reduce((first, second) -> second).orElse(BigDecimal.ZERO);
        String completeness = tokenEvents == 0 || events.stream().anyMatch(event -> event.eventTimeMs() == null) ? "partial" : "exact";
        return new ReplayMetricSummary(events.size(), messages, tools, commands, files, tokenEvents, totalTokens, cachedTokens, cost, completeness);
    }

    private List<ReplayWarning> extractionWarnings(ReplayExtractionResult extraction, ReplayMetricSummary metrics) {
        List<ReplayWarning> warnings = new ArrayList<>();
        for (String warning : extraction.warnings()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("malformedLines", extraction.malformedLines());
            evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
            evidence.put("rolloutReadable", extraction.rolloutReadable());
            warnings.add(new ReplayWarning("warning", warning, warning.replace('_', ' '), evidence));
        }
        if (metrics.tokenEvents() == 0) {
            warnings.add(new ReplayWarning("info", "token_overlay_not_available", "Token overlays are not available for this replay.", Map.of("tokenEvents", 0)));
        }
        return warnings.stream().limit(25).toList();
    }

    private ReplayDailyAnalytics daily(String day, List<ReplaySession> sessions) {
        int events = sessions.stream().mapToInt(session -> session.metrics().totalEvents()).sum();
        int messages = sessions.stream().mapToInt(session -> session.metrics().messageEvents()).sum();
        int tools = sessions.stream().mapToInt(session -> session.metrics().toolEvents()).sum();
        int commands = sessions.stream().mapToInt(session -> session.metrics().commandEvents()).sum();
        long tokens = sessions.stream().mapToLong(session -> session.metrics().totalTokens()).sum();
        BigDecimal cost = sessions.stream().map(session -> session.metrics().estimatedCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReplayDailyAnalytics(day, sessions.size(), events, messages, tools, commands, tokens, cost);
    }

    private String dataCompleteness(ReplayExtractionResult extraction) {
        if (!extraction.rolloutPathPresent()) {
            return "not_available";
        }
        if (!extraction.rolloutReadable() || extraction.malformedLines() > 0) {
            return "partial";
        }
        return "exact";
    }

    private String dataCompleteness(List<ReplaySession> sessions) {
        if (sessions.isEmpty()) {
            return "not_available";
        }
        return sessions.stream().allMatch(session -> "exact".equals(session.dataCompleteness())) ? "exact" : "partial";
    }

    private int boundedSize(int size) {
        if (size <= 0) {
            return 50;
        }
        return Math.min(size, 200);
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
}
