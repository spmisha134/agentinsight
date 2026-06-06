package com.agentinsight.usageoptimization.service;

import com.agentinsight.commandanalytics.model.CommandAnalyticsSummary;
import com.agentinsight.commandanalytics.model.CommandSessionAnalytics;
import com.agentinsight.contextmemory.model.ContextAnalyticsSummary;
import com.agentinsight.contextmemory.model.ContextSessionAnalytics;
import com.agentinsight.usageoptimization.model.OptimizationSignal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RepositoryOptimizationDetector {
    private final OptimizationSignalFactory factory;

    public RepositoryOptimizationDetector(OptimizationSignalFactory factory) {
        this.factory = factory;
    }

    public List<OptimizationSignal> detect(ContextAnalyticsSummary contexts, CommandAnalyticsSummary commands) {
        List<OptimizationSignal> signals = new ArrayList<>();
        Map<String, List<ContextSessionAnalytics>> byRepo = contexts.sessions().stream()
            .collect(Collectors.groupingBy(ContextSessionAnalytics::repositoryId, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<CommandSessionAnalytics>> commandsByRepo = commands.sessions().stream()
            .collect(Collectors.groupingBy(CommandSessionAnalytics::repositoryId, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<ContextSessionAnalytics>> entry : byRepo.entrySet()) {
            String repositoryId = entry.getKey();
            List<ContextSessionAnalytics> sessions = entry.getValue();
            if (sessions.size() < 2) {
                continue;
            }
            long withoutMemory = sessions.stream().filter(session -> session.memoryReferenceCount() == 0).count();
            if (withoutMemory >= Math.ceil(sessions.size() * 0.75)) {
                long impactTokens = Math.round(sessions.stream().mapToLong(session -> session.metrics().totalContextTokens()).sum() * 0.10);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("repositoryId", repositoryId);
                evidence.put("sessions", sessions.size());
                evidence.put("sessionsWithoutMemory", withoutMemory);
                evidence.put("metricSource", "context_memory");
                evidence.put("rawContentReturned", false);
                signals.add(factory.signal(
                    "repository_memory",
                    "medium",
                    "Repository is missing reusable agent guidance",
                    "Create or update AGENTS.md with setup, test, build, and review rules for this repository.",
                    impactTokens,
                    BigDecimal.ZERO,
                    sessions.stream().map(ContextSessionAnalytics::sessionId).limit(20).toList(),
                    repositoryId,
                    "mixed",
                    evidence
                ));
            }
        }

        for (Map.Entry<String, List<CommandSessionAnalytics>> entry : commandsByRepo.entrySet()) {
            List<CommandSessionAnalytics> sessions = entry.getValue();
            boolean hasTestCommands = sessions.stream()
                .flatMap(session -> session.categories().stream())
                .anyMatch(category -> "test".equals(category.category()));
            if (sessions.size() >= 2 && !hasTestCommands) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("repositoryId", entry.getKey());
                evidence.put("sessions", sessions.size());
                evidence.put("hasTestCommands", false);
                evidence.put("metricSource", "command_analytics");
                evidence.put("rawContentReturned", false);
                signals.add(factory.signal(
                    "repository_workflow",
                    "low",
                    "No test command pattern detected",
                    "Document the preferred test command in AGENTS.md or project docs so future sessions can validate changes consistently.",
                    0L,
                    BigDecimal.ZERO,
                    sessions.stream().map(CommandSessionAnalytics::sessionId).limit(20).toList(),
                    entry.getKey(),
                    "mixed",
                    evidence
                ));
            }
        }
        return signals;
    }
}
