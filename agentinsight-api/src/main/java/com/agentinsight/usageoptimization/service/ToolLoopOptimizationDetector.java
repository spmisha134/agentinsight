package com.agentinsight.usageoptimization.service;

import com.agentinsight.commandanalytics.model.CommandAnalyticsSummary;
import com.agentinsight.commandanalytics.model.CommandSessionAnalytics;
import com.agentinsight.toolanalytics.model.ToolAnalyticsSummary;
import com.agentinsight.toolanalytics.model.ToolSessionAnalytics;
import com.agentinsight.usageoptimization.model.OptimizationSignal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolLoopOptimizationDetector {
    private final OptimizationSignalFactory factory;

    public ToolLoopOptimizationDetector(OptimizationSignalFactory factory) {
        this.factory = factory;
    }

    public List<OptimizationSignal> detect(ToolAnalyticsSummary tools, CommandAnalyticsSummary commands) {
        List<OptimizationSignal> signals = new ArrayList<>();
        for (ToolSessionAnalytics session : tools.sessions()) {
            boolean loop = session.warnings().stream().anyMatch(warning -> "tool_loop_detected".equals(warning.code()));
            if (loop || session.metrics().failureRate() >= 0.50 || session.metrics().retryRate() >= 0.25) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("sessionId", session.sessionId());
                evidence.put("failureRate", session.metrics().failureRate());
                evidence.put("retryRate", session.metrics().retryRate());
                evidence.put("metricSource", "tool_call_analytics");
                evidence.put("rawContentReturned", false);
                signals.add(factory.signal(
                    "tool_loop",
                    loop ? "high" : "medium",
                    "Tool loop or repeated tool failure",
                    "Inspect the failing tool pattern and provide a narrower next step, expected command, or missing file path before retrying.",
                    0L,
                    BigDecimal.ZERO,
                    List.of(session.sessionId()),
                    session.repositoryId(),
                    session.model(),
                    evidence
                ));
            }
        }
        for (CommandSessionAnalytics session : commands.sessions()) {
            boolean repeated = session.warnings().stream().anyMatch(warning -> "repeated_command_detected".equals(warning.code()));
            if (repeated || session.metrics().failureRate() >= 0.50 || session.metrics().retryRate() >= 0.25) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("sessionId", session.sessionId());
                evidence.put("failureRate", session.metrics().failureRate());
                evidence.put("retryRate", session.metrics().retryRate());
                evidence.put("metricSource", "command_analytics");
                evidence.put("rawContentReturned", false);
                signals.add(factory.signal(
                    "command_workflow",
                    repeated ? "high" : "medium",
                    "Repeated command failure",
                    "Capture the known-good build or test command in repository instructions and avoid rerunning unchanged failing commands.",
                    0L,
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
}
