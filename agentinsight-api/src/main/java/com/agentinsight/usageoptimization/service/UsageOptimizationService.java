package com.agentinsight.usageoptimization.service;

import com.agentinsight.cacheperformance.model.CachePerformanceSummary;
import com.agentinsight.cacheperformance.service.CachePerformanceService;
import com.agentinsight.commandanalytics.model.CommandAnalyticsSummary;
import com.agentinsight.commandanalytics.service.CommandAnalyticsService;
import com.agentinsight.contextmemory.model.ContextAnalyticsSummary;
import com.agentinsight.contextmemory.service.ContextMemoryAnalyticsService;
import com.agentinsight.toolanalytics.model.ToolAnalyticsSummary;
import com.agentinsight.toolanalytics.service.ToolCallAnalyticsService;
import com.agentinsight.usageoptimization.model.OptimizationSession;
import com.agentinsight.usageoptimization.model.OptimizationSignal;
import com.agentinsight.usageoptimization.model.OptimizationWarning;
import com.agentinsight.usageoptimization.model.UsageOptimizationDaily;
import com.agentinsight.usageoptimization.model.UsageOptimizationSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UsageOptimizationService {
    private final CachePerformanceService cacheService;
    private final ContextMemoryAnalyticsService contextService;
    private final ToolCallAnalyticsService toolService;
    private final CommandAnalyticsService commandService;
    private final CostWasteDetector costWasteDetector;
    private final ContextWasteDetector contextWasteDetector;
    private final ToolLoopOptimizationDetector toolLoopDetector;
    private final RepositoryOptimizationDetector repositoryDetector;

    public UsageOptimizationService(
        CachePerformanceService cacheService,
        ContextMemoryAnalyticsService contextService,
        ToolCallAnalyticsService toolService,
        CommandAnalyticsService commandService,
        CostWasteDetector costWasteDetector,
        ContextWasteDetector contextWasteDetector,
        ToolLoopOptimizationDetector toolLoopDetector,
        RepositoryOptimizationDetector repositoryDetector
    ) {
        this.cacheService = cacheService;
        this.contextService = contextService;
        this.toolService = toolService;
        this.commandService = commandService;
        this.costWasteDetector = costWasteDetector;
        this.contextWasteDetector = contextWasteDetector;
        this.toolLoopDetector = toolLoopDetector;
        this.repositoryDetector = repositoryDetector;
    }

    public UsageOptimizationSummary summary(String repositoryId, String model, String day, String status, String category, String severity) {
        SourceAnalytics source = source(repositoryId, model, day, status);
        List<OptimizationSignal> signals = filter(signals(source), category, severity, null, repositoryId);
        List<OptimizationSession> sessions = sessions(source, signals);
        return new UsageOptimizationSummary(
            Instant.now(),
            dataCompleteness(source),
            sessions.size(),
            signals.size(),
            countSeverity(signals, "high"),
            countSeverity(signals, "medium"),
            countSeverity(signals, "low"),
            signals.stream().mapToLong(OptimizationSignal::impactTokens).sum(),
            signals.stream().map(OptimizationSignal::impactCost).reduce(BigDecimal.ZERO, BigDecimal::add),
            warnings(source, signals).stream().map(OptimizationWarning::code).distinct().toList(),
            signals.stream().sorted(signalComparator()).limit(100).toList(),
            sessions
        );
    }

    public List<UsageOptimizationDaily> daily(String repositoryId, String model, String status, String category, String severity) {
        SourceAnalytics source = source(repositoryId, model, null, status);
        List<OptimizationSignal> signals = filter(signals(source), category, severity, null, repositoryId);
        Map<String, List<OptimizationSignal>> byDay = signals.stream()
            .collect(Collectors.groupingBy(signal -> signalDay(signal, source), LinkedHashMap::new, Collectors.toList()));
        return byDay.entrySet().stream()
            .map(entry -> daily(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(UsageOptimizationDaily::day))
            .toList();
    }

    public Optional<OptimizationSession> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        SourceAnalytics source = source(null, null, null, null);
        List<OptimizationSignal> signals = filter(signals(source), null, null, sessionId, null);
        return sessions(source, signals).stream()
            .filter(session -> session.sessionId().equals(sessionId))
            .findFirst();
    }

    public Optional<UsageOptimizationSummary> repositorySummary(String repositoryId) {
        UsageOptimizationSummary summary = summary(repositoryId, null, null, null, null, null);
        return summary.totalSessions() == 0 && summary.totalSignals() == 0 ? Optional.empty() : Optional.of(summary);
    }

    public List<OptimizationWarning> warnings(String repositoryId, String model, String day, String status, String category, String severity) {
        SourceAnalytics source = source(repositoryId, model, day, status);
        return warnings(source, filter(signals(source), category, severity, null, repositoryId));
    }

    private SourceAnalytics source(String repositoryId, String model, String day, String status) {
        return new SourceAnalytics(
            cacheService.summary(repositoryId, model, day, status),
            contextService.summary(repositoryId, model, day, status),
            toolService.summary(repositoryId, model, day, status, null),
            commandService.summary(repositoryId, model, day, status, null)
        );
    }

    private List<OptimizationSignal> signals(SourceAnalytics source) {
        return java.util.stream.Stream.of(
                costWasteDetector.detect(source.cache()).stream(),
                contextWasteDetector.detect(source.context()).stream(),
                toolLoopDetector.detect(source.tools(), source.commands()).stream(),
                repositoryDetector.detect(source.context(), source.commands()).stream()
            )
            .flatMap(stream -> stream)
            .sorted(signalComparator())
            .toList();
    }

    private List<OptimizationSignal> filter(List<OptimizationSignal> signals, String category, String severity, String sessionId, String repositoryId) {
        return signals.stream()
            .filter(signal -> category == null || category.isBlank() || category.equals(signal.category()))
            .filter(signal -> severity == null || severity.isBlank() || severity.equals(signal.severity()))
            .filter(signal -> sessionId == null || sessionId.isBlank() || signal.affectedSessions().contains(sessionId))
            .filter(signal -> repositoryId == null || repositoryId.isBlank() || repositoryId.equals(signal.repositoryId()))
            .toList();
    }

    private List<OptimizationSession> sessions(SourceAnalytics source, List<OptimizationSignal> signals) {
        Map<String, List<OptimizationSignal>> bySession = signals.stream()
            .flatMap(signal -> signal.affectedSessions().stream().map(sessionId -> Map.entry(sessionId, signal)))
            .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return source.cache().sessions().stream()
            .filter(session -> bySession.containsKey(session.sessionId()))
            .map(session -> {
                List<OptimizationSignal> sessionSignals = bySession.get(session.sessionId()).stream().sorted(signalComparator()).toList();
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("metricSource", "usage_optimization");
                evidence.put("rawContentReturned", false);
                evidence.put("signalIds", sessionSignals.stream().map(OptimizationSignal::id).toList());
                return new OptimizationSession(
                    session.sessionId(),
                    session.title(),
                    session.cwd(),
                    session.repositoryId(),
                    session.model(),
                    session.createdAtMs(),
                    session.updatedAtMs(),
                    session.dataCompleteness(),
                    sessionSignals.size(),
                    sessionSignals.stream().mapToLong(OptimizationSignal::impactTokens).sum(),
                    sessionSignals.stream().map(OptimizationSignal::impactCost).reduce(BigDecimal.ZERO, BigDecimal::add),
                    sessionSignals,
                    evidence
                );
            })
            .sorted(Comparator.comparing(OptimizationSession::signalCount).reversed().thenComparing(OptimizationSession::updatedAtMs).reversed())
            .limit(100)
            .toList();
    }

    private List<OptimizationWarning> warnings(SourceAnalytics source, List<OptimizationSignal> signals) {
        List<OptimizationWarning> warnings = new java.util.ArrayList<>();
        if (!"exact".equals(dataCompleteness(source))) {
            warnings.add(new OptimizationWarning("warning", "partial_source_data", "Some optimization signals are based on partial source analytics.", Map.of("dataCompleteness", dataCompleteness(source))));
        }
        if (signals.isEmpty()) {
            warnings.add(new OptimizationWarning("info", "optimization_signals_not_available", "No optimization recommendations are available for the current filters.", Map.of("signals", 0)));
        }
        return warnings;
    }

    private UsageOptimizationDaily daily(String day, List<OptimizationSignal> signals) {
        long sessions = signals.stream().flatMap(signal -> signal.affectedSessions().stream()).distinct().count();
        return new UsageOptimizationDaily(
            day,
            (int) sessions,
            signals.size(),
            countSeverity(signals, "high"),
            signals.stream().mapToLong(OptimizationSignal::impactTokens).sum(),
            signals.stream().map(OptimizationSignal::impactCost).reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private String signalDay(OptimizationSignal signal, SourceAnalytics source) {
        String sessionId = signal.affectedSessions().isEmpty() ? null : signal.affectedSessions().getFirst();
        if (sessionId == null) {
            return "unknown";
        }
        return source.cache().sessions().stream()
            .filter(session -> session.sessionId().equals(sessionId))
            .findFirst()
            .map(session -> day(session.updatedAtMs()))
            .orElse("unknown");
    }

    private String dataCompleteness(SourceAnalytics source) {
        if (List.of(source.cache().dataCompleteness(), source.context().dataCompleteness(), source.tools().dataCompleteness(), source.commands().dataCompleteness()).stream().allMatch("exact"::equals)) {
            return "exact";
        }
        if (source.cache().totalSessions() == 0 && source.context().totalSessions() == 0 && source.tools().totalSessions() == 0 && source.commands().totalSessions() == 0) {
            return "not_available";
        }
        return "partial";
    }

    private int countSeverity(List<OptimizationSignal> signals, String severity) {
        return (int) signals.stream().filter(signal -> severity.equals(signal.severity())).count();
    }

    private Comparator<OptimizationSignal> signalComparator() {
        return Comparator
            .comparingInt((OptimizationSignal signal) -> severityRank(signal.severity())).reversed()
            .thenComparing(OptimizationSignal::impactTokens, Comparator.reverseOrder())
            .thenComparing(OptimizationSignal::id);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private String day(long epochMs) {
        if (epochMs <= 0L) {
            return "unknown";
        }
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC).toString();
    }

    private record SourceAnalytics(
        CachePerformanceSummary cache,
        ContextAnalyticsSummary context,
        ToolAnalyticsSummary tools,
        CommandAnalyticsSummary commands
    ) {}
}
