package com.agentinsight.usageoptimization.service;

import com.agentinsight.cacheperformance.model.CachePerformanceSummary;
import com.agentinsight.cacheperformance.model.CacheSessionAnalytics;
import com.agentinsight.usageoptimization.model.OptimizationSignal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CostWasteDetector {
    private static final long LARGE_SESSION_TOKENS = 50_000L;
    private static final long CACHE_MISS_INPUT_TOKENS = 5_000L;

    private final OptimizationSignalFactory factory;

    public CostWasteDetector(OptimizationSignalFactory factory) {
        this.factory = factory;
    }

    public List<OptimizationSignal> detect(CachePerformanceSummary summary) {
        List<OptimizationSignal> signals = new ArrayList<>();
        for (CacheSessionAnalytics session : summary.sessions()) {
            if (session.metrics().totalTokens() >= LARGE_SESSION_TOKENS) {
                Map<String, Object> evidence = evidence(session);
                evidence.put("totalTokens", session.metrics().totalTokens());
                signals.add(factory.signal(
                    "cost_waste",
                    "medium",
                    "Large token-heavy session",
                    "Review the prompt and workflow for this session; split broad tasks into smaller turns and keep only necessary context attached.",
                    Math.round(session.metrics().totalTokens() * 0.10),
                    BigDecimal.ZERO,
                    List.of(session.sessionId()),
                    session.repositoryId(),
                    session.model(),
                    evidence
                ));
            }
            if (session.metrics().uncachedInputTokens() >= CACHE_MISS_INPUT_TOKENS && session.metrics().uncachedInputRatio() >= 0.70) {
                Map<String, Object> evidence = evidence(session);
                evidence.put("uncachedInputTokens", session.metrics().uncachedInputTokens());
                evidence.put("uncachedInputRatio", session.metrics().uncachedInputRatio());
                signals.add(factory.signal(
                    "cache_miss",
                    severity(session.metrics().estimatedAdditionalSavingsOpportunity()),
                    "High uncached input",
                    "Move stable project instructions and reusable context into durable project memory so repeated turns can benefit from caching.",
                    Math.round(session.metrics().uncachedInputTokens() * 0.25),
                    session.metrics().estimatedAdditionalSavingsOpportunity(),
                    List.of(session.sessionId()),
                    session.repositoryId(),
                    session.model(),
                    evidence
                ));
            }
        }
        return signals;
    }

    private Map<String, Object> evidence(CacheSessionAnalytics session) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sessionId", session.sessionId());
        evidence.put("metricSource", "cache_performance");
        evidence.put("dataCompleteness", session.dataCompleteness());
        evidence.put("rawContentReturned", false);
        return evidence;
    }

    private String severity(BigDecimal opportunity) {
        return opportunity != null && opportunity.compareTo(new BigDecimal("0.01")) >= 0 ? "high" : "medium";
    }
}
