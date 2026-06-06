package com.agentinsight.cacheperformance.service;

import com.agentinsight.cacheperformance.model.CacheDailyAnalytics;
import com.agentinsight.cacheperformance.model.CacheExtractionResult;
import com.agentinsight.cacheperformance.model.CacheMetricSummary;
import com.agentinsight.cacheperformance.model.CachePerformanceSummary;
import com.agentinsight.cacheperformance.model.CacheSessionAnalytics;
import com.agentinsight.cacheperformance.model.CacheTokenEvent;
import com.agentinsight.cacheperformance.model.CacheWarning;
import com.agentinsight.cost.service.PricingService;
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
public class CachePerformanceService {
    private static final int MAX_SESSION_WARNINGS = 25;

    private final CodexStateRepository repository;
    private final CacheEventExtractor extractor;
    private final CacheMetricCalculator calculator;
    private final PricingService pricingService;

    public CachePerformanceService(CodexStateRepository repository, CacheEventExtractor extractor, CacheMetricCalculator calculator, PricingService pricingService) {
        this.repository = repository;
        this.extractor = extractor;
        this.calculator = calculator;
        this.pricingService = pricingService;
    }

    public CachePerformanceSummary summary(String repositoryId, String model, String day, String status) {
        List<CacheSessionAnalytics> sessions = filteredSessions(repositoryId, model, day, status);
        long inputTokens = sessions.stream().mapToLong(session -> session.metrics().inputTokens()).sum();
        long cachedInputTokens = sessions.stream().mapToLong(session -> session.metrics().cachedInputTokens()).sum();
        long uncachedInputTokens = sessions.stream().mapToLong(session -> session.metrics().uncachedInputTokens()).sum();
        long totalTokens = sessions.stream().mapToLong(session -> session.metrics().totalTokens()).sum();
        BigDecimal savings = sessions.stream().map(session -> session.metrics().estimatedCacheSavings()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal opportunity = sessions.stream().map(session -> session.metrics().estimatedAdditionalSavingsOpportunity()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CachePerformanceSummary(
            Instant.now(),
            dataCompleteness(sessions),
            sessions.size(),
            inputTokens,
            cachedInputTokens,
            uncachedInputTokens,
            totalTokens,
            ratio(cachedInputTokens, inputTokens),
            savings,
            opportunity,
            sessions.stream().flatMap(session -> session.warnings().stream()).map(CacheWarning::code).distinct().toList(),
            sessions
        );
    }

    public List<CacheDailyAnalytics> daily(String repositoryId, String model, String status) {
        return filteredSessions(repositoryId, model, null, status).stream()
            .collect(Collectors.groupingBy(session -> day(session.updatedAtMs()), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> daily(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(CacheDailyAnalytics::day))
            .toList();
    }

    public Optional<CacheSessionAnalytics> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return analytics().stream()
            .filter(session -> session.sessionId().equals(sessionId))
            .findFirst();
    }

    public Optional<CachePerformanceSummary> repositorySummary(String repositoryId) {
        CachePerformanceSummary summary = summary(repositoryId, null, null, null);
        return summary.totalSessions() == 0 ? Optional.empty() : Optional.of(summary);
    }

    public List<CacheWarning> warnings(String repositoryId, String model, String day, String status) {
        return filteredSessions(repositoryId, model, day, status).stream()
            .flatMap(session -> session.warnings().stream())
            .toList();
    }

    public List<CacheSessionAnalytics> sessions(String repositoryId, String model, String day, String status) {
        return filteredSessions(repositoryId, model, day, status);
    }

    private List<CacheSessionAnalytics> filteredSessions(String repositoryId, String model, String day, String status) {
        return analytics().stream()
            .filter(session -> repositoryId == null || repositoryId.isBlank() || repositoryId.equals(session.repositoryId()))
            .filter(session -> model == null || model.isBlank() || model.equals(session.model()))
            .filter(session -> day == null || day.isBlank() || day.equals(day(session.updatedAtMs())))
            .filter(session -> status == null || status.isBlank() || status.equals(session.dataCompleteness()))
            .toList();
    }

    private List<CacheSessionAnalytics> analytics() {
        return repository.findThreads().stream()
            .map(this::analyze)
            .sorted(Comparator.comparingLong(CacheSessionAnalytics::updatedAtMs).reversed())
            .toList();
    }

    private CacheSessionAnalytics analyze(RawThread thread) {
        String model = blankToUnknown(thread.model());
        CacheExtractionResult extraction = extractor.extract(thread.id(), thread.rolloutPath());
        List<CacheTokenEvent> events = extraction.tokenEvents();
        CacheMetricSummary metrics = calculator.calculate(events, pricingService.pricingFor(model));
        List<CacheWarning> warnings = new ArrayList<>(calculator.warnings(metrics));
        warnings.addAll(extractionWarnings(extraction));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
        evidence.put("rolloutReadable", extraction.rolloutReadable());
        evidence.put("malformedLines", extraction.malformedLines());
        evidence.put("tokenUsageLineNumbers", events.stream().map(CacheTokenEvent::lineNumber).limit(100).toList());
        evidence.put("metricSource", "total_token_usage");
        evidence.put("rawContentReturned", false);

        return new CacheSessionAnalytics(
            thread.id(),
            thread.title(),
            thread.cwd(),
            repositoryId(thread),
            model,
            thread.createdAtMs(),
            thread.updatedAtMs(),
            events.size(),
            metrics,
            dataCompleteness(extraction, metrics),
            warnings.stream().limit(MAX_SESSION_WARNINGS).toList(),
            evidence
        );
    }

    private List<CacheWarning> extractionWarnings(CacheExtractionResult extraction) {
        List<CacheWarning> warnings = new ArrayList<>();
        for (String warning : extraction.warnings()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("malformedLines", extraction.malformedLines());
            evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
            evidence.put("rolloutReadable", extraction.rolloutReadable());
            warnings.add(new CacheWarning("warning", warning, warning.replace('_', ' '), evidence));
        }
        return warnings;
    }

    private CacheDailyAnalytics daily(String day, List<CacheSessionAnalytics> sessions) {
        long inputTokens = sessions.stream().mapToLong(session -> session.metrics().inputTokens()).sum();
        long cachedInputTokens = sessions.stream().mapToLong(session -> session.metrics().cachedInputTokens()).sum();
        long uncachedInputTokens = sessions.stream().mapToLong(session -> session.metrics().uncachedInputTokens()).sum();
        BigDecimal savings = sessions.stream().map(session -> session.metrics().estimatedCacheSavings()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CacheDailyAnalytics(day, sessions.size(), inputTokens, cachedInputTokens, uncachedInputTokens, ratio(cachedInputTokens, inputTokens), savings);
    }

    private String dataCompleteness(CacheExtractionResult extraction, CacheMetricSummary metrics) {
        if (!extraction.rolloutPathPresent() || "not_available".equals(metrics.metricCompleteness())) {
            return "not_available";
        }
        if (!extraction.rolloutReadable() || extraction.malformedLines() > 0) {
            return "partial";
        }
        return "exact";
    }

    private String dataCompleteness(List<CacheSessionAnalytics> sessions) {
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
