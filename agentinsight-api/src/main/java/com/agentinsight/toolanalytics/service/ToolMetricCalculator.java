package com.agentinsight.toolanalytics.service;

import com.agentinsight.toolanalytics.model.NormalizedToolCall;
import com.agentinsight.toolanalytics.model.ToolMetricSummary;
import com.agentinsight.toolanalytics.model.ToolUsageStat;
import com.agentinsight.toolanalytics.model.ToolWarning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolMetricCalculator {
    private static final int LOOP_THRESHOLD = 4;

    public ToolMetricSummary calculate(List<NormalizedToolCall> calls) {
        int total = calls.size();
        int failed = (int) calls.stream().filter(call -> "failed".equals(call.status())).count();
        int succeeded = (int) calls.stream().filter(call -> "succeeded".equals(call.status())).count();
        int partial = (int) calls.stream().filter(call -> "started".equals(call.status()) || "unknown".equals(call.status())).count();
        int retries = retryCount(calls);
        long outputBytes = calls.stream().mapToLong(NormalizedToolCall::outputSizeBytes).sum();
        Long averageLatency = averageLatency(calls);
        String completeness = calls.stream().anyMatch(call -> call.eventTimeMs() == null) ? "partial" : "exact";
        return new ToolMetricSummary(total, succeeded, failed, partial, retries, outputBytes, averageLatency, ratio(failed, total), ratio(retries, total), completeness);
    }

    public List<ToolUsageStat> toolStats(List<NormalizedToolCall> calls) {
        return calls.stream()
            .collect(Collectors.groupingBy(call -> call.toolName() + "\u0000" + call.toolType(), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> toolStat(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(ToolUsageStat::calls).reversed())
            .limit(20)
            .toList();
    }

    public List<ToolWarning> warnings(List<NormalizedToolCall> calls) {
        List<ToolWarning> warnings = new ArrayList<>();
        warnings.addAll(loopWarnings(calls));
        long failed = calls.stream().filter(call -> "failed".equals(call.status())).count();
        if (!calls.isEmpty() && ratio(failed, calls.size()) >= 0.5) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("toolCalls", calls.size());
            evidence.put("failedToolCalls", failed);
            warnings.add(new ToolWarning("warning", "high_tool_failure_rate", "Tool failure rate is high.", evidence));
        }
        return warnings.stream().limit(25).toList();
    }

    private ToolUsageStat toolStat(String key, List<NormalizedToolCall> calls) {
        String[] parts = key.split("\u0000", -1);
        int failures = (int) calls.stream().filter(call -> "failed".equals(call.status())).count();
        long outputBytes = calls.stream().mapToLong(NormalizedToolCall::outputSizeBytes).sum();
        return new ToolUsageStat(parts[0], parts[1], calls.size(), failures, ratio(failures, calls.size()), outputBytes, averageLatency(calls));
    }

    private List<ToolWarning> loopWarnings(List<NormalizedToolCall> calls) {
        List<ToolWarning> warnings = new ArrayList<>();
        Map<String, Integer> consecutive = new LinkedHashMap<>();
        String previousKey = null;
        int previousCount = 0;
        for (NormalizedToolCall call : calls) {
            String key = call.toolName() + "|" + call.argumentsHash() + "|" + call.status();
            previousCount = Objects.equals(key, previousKey) ? previousCount + 1 : 1;
            previousKey = key;
            consecutive.merge(key, previousCount, Math::max);
        }
        for (Map.Entry<String, Integer> entry : consecutive.entrySet()) {
            if (entry.getValue() < LOOP_THRESHOLD) {
                continue;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("repeatCount", entry.getValue());
            evidence.put("signature", Integer.toHexString(entry.getKey().hashCode()));
            warnings.add(new ToolWarning("warning", "tool_loop_detected", "Repeated tool loop detected.", evidence));
        }
        return warnings;
    }

    private int retryCount(List<NormalizedToolCall> calls) {
        Map<String, Long> bySignature = calls.stream()
            .filter(call -> call.argumentsHash() != null)
            .collect(Collectors.groupingBy(call -> call.toolName() + "|" + call.argumentsHash(), Collectors.counting()));
        return bySignature.values().stream().mapToInt(count -> Math.max(0, count.intValue() - 1)).sum();
    }

    private Long averageLatency(List<NormalizedToolCall> calls) {
        Map<String, List<NormalizedToolCall>> withIds = calls.stream()
            .filter(call -> call.toolCallId() != null && call.eventTimeMs() != null)
            .collect(Collectors.groupingBy(NormalizedToolCall::toolCallId));
        List<Long> latencies = new ArrayList<>();
        for (List<NormalizedToolCall> group : withIds.values()) {
            Long start = group.stream().filter(call -> "started".equals(call.status())).map(NormalizedToolCall::eventTimeMs).min(Long::compareTo).orElse(null);
            Long end = group.stream().filter(call -> "succeeded".equals(call.status()) || "failed".equals(call.status())).map(NormalizedToolCall::eventTimeMs).max(Long::compareTo).orElse(null);
            if (start != null && end != null && end >= start) {
                latencies.add(end - start);
            }
        }
        if (latencies.isEmpty()) {
            return null;
        }
        return Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }
}
