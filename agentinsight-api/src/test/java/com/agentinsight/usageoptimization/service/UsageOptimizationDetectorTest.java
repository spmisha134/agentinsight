package com.agentinsight.usageoptimization.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.cacheperformance.model.CacheMetricSummary;
import com.agentinsight.cacheperformance.model.CachePerformanceSummary;
import com.agentinsight.cacheperformance.model.CacheSessionAnalytics;
import com.agentinsight.commandanalytics.model.CommandAnalyticsSummary;
import com.agentinsight.commandanalytics.model.CommandMetricSummary;
import com.agentinsight.commandanalytics.model.CommandSessionAnalytics;
import com.agentinsight.commandanalytics.model.CommandWarning;
import com.agentinsight.contextmemory.model.ContextAnalyticsSummary;
import com.agentinsight.contextmemory.model.ContextMetricSummary;
import com.agentinsight.contextmemory.model.ContextSessionAnalytics;
import com.agentinsight.toolanalytics.model.ToolAnalyticsSummary;
import com.agentinsight.toolanalytics.model.ToolMetricSummary;
import com.agentinsight.toolanalytics.model.ToolSessionAnalytics;
import com.agentinsight.toolanalytics.model.ToolWarning;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageOptimizationDetectorTest {
    private final OptimizationSignalFactory factory = new OptimizationSignalFactory();

    @Test
    void detectsCostCacheAndContextWasteSignals() {
        var costSignals = new CostWasteDetector(factory).detect(cacheSummary(cacheSession(
            "s1",
            80_000,
            2_000,
            30_000,
            0.93,
            new BigDecimal("0.025")
        )));
        var contextSignals = new ContextWasteDetector(factory).detect(contextSummary(contextSession(
            "s1",
            10_000,
            3_000,
            0.30,
            0
        )));

        assertThat(costSignals).extracting("category").contains("cost_waste", "cache_miss");
        assertThat(costSignals).extracting("severity").contains("high");
        assertThat(contextSignals).extracting("category").contains("context_waste", "repository_memory");
        assertThat(contextSignals).allMatch(signal -> !signal.evidence().toString().contains("secret"));
    }

    @Test
    void detectsToolAndCommandWorkflowSignals() {
        var toolSignals = new ToolLoopOptimizationDetector(factory).detect(
            toolSummary(toolSession("s1", 0.75, 0.30, List.of(new ToolWarning("warning", "tool_loop_detected", "loop", Map.of())))),
            commandSummary(commandSession("s2", 0.80, 0.40, List.of(new CommandWarning("warning", "repeated_command_detected", "repeat", Map.of()))))
        );

        assertThat(toolSignals).extracting("category").contains("tool_loop", "command_workflow");
        assertThat(toolSignals).extracting("severity").contains("high");
    }

    @Test
    void detectsRepositoryLevelMissingMemoryAndTests() {
        var detector = new RepositoryOptimizationDetector(factory);
        var signals = detector.detect(
            contextSummary(
                contextSession("s1", 4_000, 0, 0.0, 0),
                contextSession("s2", 5_000, 0, 0.0, 0)
            ),
            commandSummary(
                commandSession("s1", 0.0, 0.0, List.of()),
                commandSession("s2", 0.0, 0.0, List.of())
            )
        );

        assertThat(signals).extracting("category").contains("repository_memory", "repository_workflow");
    }

    @Test
    void avoidsFalsePositivesForSmallHealthySessions() {
        var costSignals = new CostWasteDetector(factory).detect(cacheSummary(cacheSession("s1", 900, 600, 100, 0.11, BigDecimal.ZERO)));
        var contextSignals = new ContextWasteDetector(factory).detect(contextSummary(contextSession("s1", 500, 0, 0.0, 1)));
        var loopSignals = new ToolLoopOptimizationDetector(factory).detect(
            toolSummary(toolSession("s1", 0.0, 0.0, List.of())),
            commandSummary(commandSession("s1", 0.0, 0.0, List.of()))
        );

        assertThat(costSignals).isEmpty();
        assertThat(contextSignals).isEmpty();
        assertThat(loopSignals).isEmpty();
    }

    private CachePerformanceSummary cacheSummary(CacheSessionAnalytics... sessions) {
        return new CachePerformanceSummary(Instant.now(), "exact", sessions.length, 0, 0, 0, 0, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(sessions));
    }

    private CacheSessionAnalytics cacheSession(String id, long totalTokens, long cachedInput, long uncachedInput, double uncachedRatio, BigDecimal opportunity) {
        var metrics = new CacheMetricSummary(cachedInput + uncachedInput, cachedInput, uncachedInput, 0, totalTokens, 0.0, uncachedRatio, BigDecimal.ZERO, opportunity, "configured", "exact");
        return new CacheSessionAnalytics(id, null, "/repo", "repo", "model", 1L, 2L, 1, metrics, "exact", List.of(), Map.of());
    }

    private ContextAnalyticsSummary contextSummary(ContextSessionAnalytics... sessions) {
        return new ContextAnalyticsSummary(Instant.now(), "exact", sessions.length, 0, 0, 0, 1.0, 0.0, List.of(), List.of(sessions));
    }

    private ContextSessionAnalytics contextSession(String id, long totalTokens, long repeatedTokens, double repeatedRatio, int memoryReferences) {
        var metrics = new ContextMetricSummary(totalTokens, repeatedTokens, 0, 0, 1.0 - repeatedRatio, repeatedRatio, "exact");
        return new ContextSessionAnalytics(id, null, "/repo", "repo", "model", 1L, 2L, 1, memoryReferences, metrics, "exact", List.of(), List.of(), Map.of());
    }

    private ToolAnalyticsSummary toolSummary(ToolSessionAnalytics... sessions) {
        return new ToolAnalyticsSummary(Instant.now(), "exact", sessions.length, 0, 0, 0, 0, null, 0.0, 0.0, List.of(), List.of(), List.of(sessions));
    }

    private ToolSessionAnalytics toolSession(String id, double failureRate, double retryRate, List<ToolWarning> warnings) {
        var metrics = new ToolMetricSummary(10, 0, (int) Math.round(failureRate * 10), 0, (int) Math.round(retryRate * 10), 0, null, failureRate, retryRate, "exact");
        return new ToolSessionAnalytics(id, null, "/repo", "repo", "model", 1L, 2L, metrics, "exact", List.of(), warnings, Map.of());
    }

    private CommandAnalyticsSummary commandSummary(CommandSessionAnalytics... sessions) {
        return new CommandAnalyticsSummary(Instant.now(), "exact", sessions.length, 0, 0, 0, 0, null, 1.0, 0.0, 0.0, List.of(), List.of(), List.of(sessions));
    }

    private CommandSessionAnalytics commandSession(String id, double failureRate, double retryRate, List<CommandWarning> warnings) {
        var metrics = new CommandMetricSummary(10, 10 - (int) Math.round(failureRate * 10), (int) Math.round(failureRate * 10), 0, (int) Math.round(retryRate * 10), 0, null, 0, 0, 1.0 - failureRate, failureRate, retryRate, "exact");
        return new CommandSessionAnalytics(id, null, "/repo", "repo", "model", 1L, 2L, metrics, "exact", List.of(), List.of(), warnings, Map.of());
    }
}
