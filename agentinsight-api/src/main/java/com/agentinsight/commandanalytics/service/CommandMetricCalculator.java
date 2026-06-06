package com.agentinsight.commandanalytics.service;

import com.agentinsight.commandanalytics.model.CommandCategoryStat;
import com.agentinsight.commandanalytics.model.CommandEvent;
import com.agentinsight.commandanalytics.model.CommandMetricSummary;
import com.agentinsight.commandanalytics.model.CommandWarning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CommandMetricCalculator {
    private static final int REPEAT_WARNING_THRESHOLD = 3;

    public CommandMetricSummary calculate(List<CommandEvent> commands) {
        int total = commands.size();
        int succeeded = (int) commands.stream().filter(command -> "succeeded".equals(command.status())).count();
        int failed = (int) commands.stream().filter(command -> "failed".equals(command.status())).count();
        int unknown = total - succeeded - failed;
        int retries = retryCount(commands);
        long duration = commands.stream().map(CommandEvent::durationMs).filter(value -> value != null).mapToLong(Long::longValue).sum();
        long stdout = commands.stream().mapToLong(CommandEvent::stdoutSizeBytes).sum();
        long stderr = commands.stream().mapToLong(CommandEvent::stderrSizeBytes).sum();
        Long averageDuration = averageDuration(commands);
        String completeness = commands.stream().anyMatch(command -> command.durationMs() == null || "unknown".equals(command.status())) ? "partial" : "exact";
        return new CommandMetricSummary(total, succeeded, failed, unknown, retries, duration, averageDuration, stdout, stderr, ratio(succeeded, total), ratio(failed, total), ratio(retries, total), completeness);
    }

    public List<CommandCategoryStat> categoryStats(List<CommandEvent> commands) {
        return commands.stream()
            .collect(Collectors.groupingBy(CommandEvent::category, LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> categoryStat(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(CommandCategoryStat::commands).reversed())
            .limit(20)
            .toList();
    }

    public List<CommandWarning> warnings(List<CommandEvent> commands) {
        List<CommandWarning> warnings = new ArrayList<>();
        warnings.addAll(riskyCommandWarnings(commands));
        warnings.addAll(repeatedCommandWarnings(commands));
        long failed = commands.stream().filter(command -> "failed".equals(command.status())).count();
        if (!commands.isEmpty() && ratio(failed, commands.size()) >= 0.5) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("commands", commands.size());
            evidence.put("failedCommands", failed);
            warnings.add(new CommandWarning("warning", "high_command_failure_rate", "Command failure rate is high.", evidence));
        }
        return warnings.stream().limit(25).toList();
    }

    private CommandCategoryStat categoryStat(String category, List<CommandEvent> commands) {
        int successes = (int) commands.stream().filter(command -> "succeeded".equals(command.status())).count();
        int failures = (int) commands.stream().filter(command -> "failed".equals(command.status())).count();
        return new CommandCategoryStat(category, commands.size(), successes, failures, retryCount(commands), ratio(failures, commands.size()), averageDuration(commands));
    }

    private List<CommandWarning> riskyCommandWarnings(List<CommandEvent> commands) {
        return commands.stream()
            .filter(command -> !"none".equals(command.riskLevel()))
            .map(command -> {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("lineNumber", command.lineNumber());
                evidence.put("commandHash", command.commandHash());
                evidence.put("commandPreview", command.commandPreview());
                evidence.put("riskReason", command.riskReason());
                return new CommandWarning(severity(command.riskLevel()), "risky_command_detected", "Risky command detected.", evidence);
            })
            .limit(10)
            .toList();
    }

    private List<CommandWarning> repeatedCommandWarnings(List<CommandEvent> commands) {
        List<CommandWarning> warnings = new ArrayList<>();
        Map<String, Integer> consecutive = new LinkedHashMap<>();
        String previousKey = null;
        int previousCount = 0;
        for (CommandEvent command : commands) {
            String key = command.commandHash() + "|" + command.status();
            previousCount = Objects.equals(key, previousKey) ? previousCount + 1 : 1;
            previousKey = key;
            consecutive.merge(key, previousCount, Math::max);
        }
        for (Map.Entry<String, Integer> entry : consecutive.entrySet()) {
            if (entry.getValue() < REPEAT_WARNING_THRESHOLD) {
                continue;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("repeatCount", entry.getValue());
            evidence.put("signature", Integer.toHexString(entry.getKey().hashCode()));
            warnings.add(new CommandWarning("warning", "repeated_command_detected", "Repeated command detected.", evidence));
        }
        return warnings;
    }

    private int retryCount(List<CommandEvent> commands) {
        Map<String, Long> byHash = commands.stream()
            .collect(Collectors.groupingBy(CommandEvent::commandHash, Collectors.counting()));
        return byHash.values().stream().mapToInt(count -> Math.max(0, count.intValue() - 1)).sum();
    }

    private String severity(String riskLevel) {
        if ("high".equals(riskLevel)) {
            return "error";
        }
        if ("medium".equals(riskLevel)) {
            return "warning";
        }
        return "info";
    }

    private Long averageDuration(List<CommandEvent> commands) {
        List<Long> values = commands.stream().map(CommandEvent::durationMs).filter(value -> value != null).toList();
        if (values.isEmpty()) {
            return null;
        }
        return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }
}
