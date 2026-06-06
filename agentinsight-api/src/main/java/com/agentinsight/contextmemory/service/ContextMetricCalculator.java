package com.agentinsight.contextmemory.service;

import com.agentinsight.contextmemory.model.ContextMetricSummary;
import com.agentinsight.contextmemory.model.ContextSegment;
import com.agentinsight.contextmemory.model.ContextWarning;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContextMetricCalculator {
    private static final long REPEAT_MIN_TOKENS = 20L;

    public ContextMetricSummary calculate(List<ContextSegment> segments) {
        long totalTokens = segments.stream().mapToLong(ContextSegment::estimatedTokens).sum();
        long repeatedTokens = repeatedTokens(segments);
        long memoryTokens = segments.stream()
            .filter(segment -> !segment.memoryReferences().isEmpty())
            .mapToLong(ContextSegment::estimatedTokens)
            .sum();
        long cacheableTokens = Math.min(totalTokens, repeatedTokens + memoryTokens);
        long growthTokens = contextGrowthTokens(segments);
        double repeatedRatio = ratio(repeatedTokens, totalTokens);
        double usefulRatio = ratio(Math.max(0L, totalTokens - repeatedTokens), totalTokens);
        return new ContextMetricSummary(totalTokens, repeatedTokens, cacheableTokens, growthTokens, usefulRatio, repeatedRatio, "estimated");
    }

    public List<ContextWarning> repeatedContextWarnings(List<ContextSegment> segments) {
        Map<String, List<ContextSegment>> byHash = byHash(segments);
        List<ContextWarning> warnings = new ArrayList<>();
        for (Map.Entry<String, List<ContextSegment>> entry : byHash.entrySet()) {
            List<ContextSegment> repeated = entry.getValue();
            if (repeated.size() < 2 || repeated.getFirst().estimatedTokens() < REPEAT_MIN_TOKENS) {
                continue;
            }
            long repeatedTokens = repeated.stream().skip(1).mapToLong(ContextSegment::estimatedTokens).sum();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("contentHash", entry.getKey());
            evidence.put("occurrences", repeated.size());
            evidence.put("lineNumbers", repeated.stream().map(ContextSegment::lineNumber).toList());
            evidence.put("estimatedRepeatedTokens", repeatedTokens);
            warnings.add(new ContextWarning("warning", "repeated_context", "Repeated context segment detected.", evidence));
        }
        return warnings;
    }

    private long repeatedTokens(List<ContextSegment> segments) {
        return byHash(segments).values().stream()
            .filter(group -> group.size() > 1 && group.getFirst().estimatedTokens() >= REPEAT_MIN_TOKENS)
            .mapToLong(group -> group.stream().skip(1).mapToLong(ContextSegment::estimatedTokens).sum())
            .sum();
    }

    private long contextGrowthTokens(List<ContextSegment> segments) {
        if (segments.isEmpty()) {
            return 0L;
        }
        long first = segments.getFirst().estimatedTokens();
        long last = segments.getLast().estimatedTokens();
        return Math.max(0L, last - first);
    }

    private Map<String, List<ContextSegment>> byHash(List<ContextSegment> segments) {
        Map<String, List<ContextSegment>> byHash = new LinkedHashMap<>();
        for (ContextSegment segment : segments) {
            byHash.computeIfAbsent(segment.contentHash(), ignored -> new ArrayList<>()).add(segment);
        }
        return byHash;
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }
}
