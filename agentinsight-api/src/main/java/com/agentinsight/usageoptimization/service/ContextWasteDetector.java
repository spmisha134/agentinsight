package com.agentinsight.usageoptimization.service;

import com.agentinsight.contextmemory.model.ContextAnalyticsSummary;
import com.agentinsight.contextmemory.model.ContextSessionAnalytics;
import com.agentinsight.usageoptimization.model.OptimizationSignal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContextWasteDetector {
    private static final long REPEATED_CONTEXT_TOKENS = 1_000L;
    private static final long MEMORY_REFERENCE_THRESHOLD = 2_000L;

    private final OptimizationSignalFactory factory;

    public ContextWasteDetector(OptimizationSignalFactory factory) {
        this.factory = factory;
    }

    public List<OptimizationSignal> detect(ContextAnalyticsSummary summary) {
        List<OptimizationSignal> signals = new ArrayList<>();
        for (ContextSessionAnalytics session : summary.sessions()) {
            if (session.metrics().repeatedContextTokens() >= REPEATED_CONTEXT_TOKENS && session.metrics().repeatedContextRatio() >= 0.20) {
                Map<String, Object> evidence = evidence(session);
                evidence.put("repeatedContextTokens", session.metrics().repeatedContextTokens());
                evidence.put("repeatedContextRatio", session.metrics().repeatedContextRatio());
                signals.add(factory.signal(
                    "context_waste",
                    "high",
                    "Repeated context is consuming tokens",
                    "Extract repeated instructions into AGENTS.md, a project doc, or a skill instead of restating them across turns.",
                    session.metrics().repeatedContextTokens(),
                    BigDecimal.ZERO,
                    List.of(session.sessionId()),
                    session.repositoryId(),
                    session.model(),
                    evidence
                ));
            }
            if (session.memoryReferenceCount() == 0 && session.metrics().totalContextTokens() >= MEMORY_REFERENCE_THRESHOLD) {
                Map<String, Object> evidence = evidence(session);
                evidence.put("memoryReferenceCount", session.memoryReferenceCount());
                evidence.put("totalContextTokens", session.metrics().totalContextTokens());
                signals.add(factory.signal(
                    "repository_memory",
                    "medium",
                    "No project memory references detected",
                    "Add stable repository instructions, test commands, and build notes to AGENTS.md so sessions start with reusable context.",
                    Math.round(session.metrics().totalContextTokens() * 0.15),
                    BigDecimal.ZERO,
                    List.of(session.sessionId()),
                    session.repositoryId(),
                    session.model(),
                    evidence
                ));
            }
        }
        return signals;
    }

    private Map<String, Object> evidence(ContextSessionAnalytics session) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sessionId", session.sessionId());
        evidence.put("metricSource", "context_memory");
        evidence.put("dataCompleteness", session.dataCompleteness());
        evidence.put("rawContentReturned", false);
        return evidence;
    }
}
