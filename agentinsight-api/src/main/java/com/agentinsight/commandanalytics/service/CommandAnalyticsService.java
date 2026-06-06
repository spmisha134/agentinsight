package com.agentinsight.commandanalytics.service;

import com.agentinsight.commandanalytics.model.CommandAnalyticsSummary;
import com.agentinsight.commandanalytics.model.CommandCategoryStat;
import com.agentinsight.commandanalytics.model.CommandDailyAnalytics;
import com.agentinsight.commandanalytics.model.CommandEvent;
import com.agentinsight.commandanalytics.model.CommandExtractionResult;
import com.agentinsight.commandanalytics.model.CommandMetricSummary;
import com.agentinsight.commandanalytics.model.CommandSessionAnalytics;
import com.agentinsight.commandanalytics.model.CommandWarning;
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
public class CommandAnalyticsService {
    private final CodexStateRepository repository;
    private final CommandExtractor extractor;
    private final CommandMetricCalculator calculator;

    public CommandAnalyticsService(CodexStateRepository repository, CommandExtractor extractor, CommandMetricCalculator calculator) {
        this.repository = repository;
        this.extractor = extractor;
        this.calculator = calculator;
    }

    public CommandAnalyticsSummary summary(String repositoryId, String model, String day, String status, String category) {
        List<CommandSessionAnalytics> sessions = filteredSessions(repositoryId, model, day, status, category);
        List<CommandCategoryStat> categories = aggregateCategories(sessions);
        int totalCommands = sessions.stream().mapToInt(session -> session.metrics().totalCommands()).sum();
        int failedCommands = sessions.stream().mapToInt(session -> session.metrics().failedCommands()).sum();
        int successfulCommands = sessions.stream().mapToInt(session -> session.metrics().successfulCommands()).sum();
        int retryCount = sessions.stream().mapToInt(session -> session.metrics().retryCount()).sum();
        long totalDuration = sessions.stream().mapToLong(session -> session.metrics().totalDurationMs()).sum();
        Long averageDuration = averageDuration(sessions);
        return new CommandAnalyticsSummary(
            Instant.now(),
            dataCompleteness(sessions),
            sessions.size(),
            totalCommands,
            failedCommands,
            retryCount,
            totalDuration,
            averageDuration,
            ratio(successfulCommands, totalCommands),
            ratio(failedCommands, totalCommands),
            ratio(retryCount, totalCommands),
            sessions.stream().flatMap(session -> session.warnings().stream()).map(CommandWarning::code).distinct().toList(),
            categories,
            sessions
        );
    }

    public List<CommandDailyAnalytics> daily(String repositoryId, String model, String status, String category) {
        return filteredSessions(repositoryId, model, null, status, category).stream()
            .collect(Collectors.groupingBy(session -> day(session.updatedAtMs()), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> daily(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(CommandDailyAnalytics::day))
            .toList();
    }

    public Optional<CommandSessionAnalytics> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return analytics().stream().filter(session -> session.sessionId().equals(sessionId)).findFirst();
    }

    public Optional<CommandAnalyticsSummary> repositorySummary(String repositoryId) {
        CommandAnalyticsSummary summary = summary(repositoryId, null, null, null, null);
        return summary.totalSessions() == 0 ? Optional.empty() : Optional.of(summary);
    }

    public List<CommandWarning> warnings(String repositoryId, String model, String day, String status, String category) {
        return filteredSessions(repositoryId, model, day, status, category).stream()
            .flatMap(session -> session.warnings().stream())
            .toList();
    }

    private List<CommandSessionAnalytics> filteredSessions(String repositoryId, String model, String day, String status, String category) {
        return analytics().stream()
            .filter(session -> repositoryId == null || repositoryId.isBlank() || repositoryId.equals(session.repositoryId()))
            .filter(session -> model == null || model.isBlank() || model.equals(session.model()))
            .filter(session -> day == null || day.isBlank() || day.equals(day(session.updatedAtMs())))
            .filter(session -> status == null || status.isBlank() || status.equals(session.dataCompleteness()))
            .filter(session -> category == null || category.isBlank() || session.categories().stream().anyMatch(stat -> category.equals(stat.category())))
            .toList();
    }

    private List<CommandSessionAnalytics> analytics() {
        return repository.findThreads().stream()
            .map(this::analyze)
            .sorted(Comparator.comparingLong(CommandSessionAnalytics::updatedAtMs).reversed())
            .toList();
    }

    private CommandSessionAnalytics analyze(RawThread thread) {
        CommandExtractionResult extraction = extractor.extract(thread.id(), thread.rolloutPath());
        List<CommandEvent> commands = extraction.commands();
        CommandMetricSummary metrics = calculator.calculate(commands);
        List<CommandWarning> warnings = new ArrayList<>(calculator.warnings(commands));
        warnings.addAll(extractionWarnings(extraction));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
        evidence.put("rolloutReadable", extraction.rolloutReadable());
        evidence.put("malformedLines", extraction.malformedLines());
        evidence.put("commandLineNumbers", commands.stream().map(CommandEvent::lineNumber).limit(100).toList());
        evidence.put("rawOutputReturned", false);

        return new CommandSessionAnalytics(
            thread.id(),
            thread.title(),
            thread.cwd(),
            repositoryId(thread),
            blankToUnknown(thread.model()),
            thread.createdAtMs(),
            thread.updatedAtMs(),
            metrics,
            dataCompleteness(extraction),
            calculator.categoryStats(commands),
            commands.stream().limit(100).toList(),
            warnings.stream().limit(25).toList(),
            evidence
        );
    }

    private List<CommandWarning> extractionWarnings(CommandExtractionResult extraction) {
        List<CommandWarning> warnings = new ArrayList<>();
        for (String warning : extraction.warnings()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("malformedLines", extraction.malformedLines());
            evidence.put("rolloutPathPresent", extraction.rolloutPathPresent());
            evidence.put("rolloutReadable", extraction.rolloutReadable());
            warnings.add(new CommandWarning("warning", warning, warning.replace('_', ' '), evidence));
        }
        return warnings;
    }

    private List<CommandCategoryStat> aggregateCategories(List<CommandSessionAnalytics> sessions) {
        Map<String, List<CommandCategoryStat>> grouped = sessions.stream()
            .flatMap(session -> session.categories().stream())
            .collect(Collectors.groupingBy(CommandCategoryStat::category, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
            .map(entry -> aggregateCategory(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(CommandCategoryStat::commands).reversed())
            .limit(20)
            .toList();
    }

    private CommandCategoryStat aggregateCategory(String category, List<CommandCategoryStat> categories) {
        int commands = categories.stream().mapToInt(CommandCategoryStat::commands).sum();
        int successes = categories.stream().mapToInt(CommandCategoryStat::successes).sum();
        int failures = categories.stream().mapToInt(CommandCategoryStat::failures).sum();
        int retries = categories.stream().mapToInt(CommandCategoryStat::retries).sum();
        List<Long> durations = categories.stream().map(CommandCategoryStat::averageDurationMs).filter(value -> value != null).toList();
        Long average = durations.isEmpty() ? null : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0.0));
        return new CommandCategoryStat(category, commands, successes, failures, retries, ratio(failures, commands), average);
    }

    private CommandDailyAnalytics daily(String day, List<CommandSessionAnalytics> sessions) {
        int commands = sessions.stream().mapToInt(session -> session.metrics().totalCommands()).sum();
        int failures = sessions.stream().mapToInt(session -> session.metrics().failedCommands()).sum();
        int retries = sessions.stream().mapToInt(session -> session.metrics().retryCount()).sum();
        long duration = sessions.stream().mapToLong(session -> session.metrics().totalDurationMs()).sum();
        return new CommandDailyAnalytics(day, sessions.size(), commands, failures, retries, duration, ratio(failures, commands));
    }

    private Long averageDuration(List<CommandSessionAnalytics> sessions) {
        List<Long> values = sessions.stream().map(session -> session.metrics().averageDurationMs()).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private String dataCompleteness(CommandExtractionResult extraction) {
        if (!extraction.rolloutPathPresent()) {
            return "not_available";
        }
        if (!extraction.rolloutReadable() || extraction.malformedLines() > 0) {
            return "partial";
        }
        return "exact";
    }

    private String dataCompleteness(List<CommandSessionAnalytics> sessions) {
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
