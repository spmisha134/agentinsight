package com.agentinsight.agentsessionreplay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.cost.model.Pricing;
import com.agentinsight.cost.service.CostCalculator;
import com.agentinsight.cost.service.PricingProperties;
import com.agentinsight.cost.service.PricingService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayTimelineBuilderTest {
    @TempDir
    Path tempDir;

    private final ReplayTimelineBuilder builder = new ReplayTimelineBuilder(
        new CostCalculator(),
        new PricingService(new PricingProperties(Map.of("default", new Pricing(
            new BigDecimal("1.00"),
            new BigDecimal("0.10"),
            new BigDecimal("2.00")
        ))))
    );

    @Test
    void buildsOrderedRedactedTimelineWithTokenAndCommandOverlays() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"please edit agentinsight-api/src/main/java/App.java"},"timestamp":"2026-06-04T10:00:00Z"}
            {"type":"tool_call_started","payload":{"tool_call_id":"call-1","tool_name":"exec","arguments":{"cmd":"./gradlew test SECRET=value"},"timestamp":"2026-06-04T10:00:01Z"}}
            {"type":"message","payload":{"role":"assistant","content":"Tests are passing"},"timestamp":"2026-06-04T10:00:02Z"}
            {"type":"token_count","payload":{"info":{"total_token_usage":{"input_tokens":1000,"cached_input_tokens":400,"output_tokens":200,"reasoning_output_tokens":100,"total_tokens":1300}}},"timestamp":"2026-06-04T10:00:03Z"}
            """);

        var result = builder.build("session-1", rollout.toString(), "repo", "main", "unknown", ReplayRedactionOptions.defaults());

        assertThat(result.malformedLines()).isZero();
        assertThat(result.events()).extracting("eventType")
            .containsExactly("user_message", "command", "assistant_message", "token_usage");
        assertThat(result.events().getFirst().contentPreview()).isEqualTo("<prompt redacted>");
        assertThat(result.events().getFirst().filePath()).isEqualTo("<redacted-path>");
        assertThat(result.events().get(1).commandPreview()).contains("SECRET=<redacted>");
        assertThat(result.events().get(3).totalTokens()).isEqualTo(1300L);
        assertThat(result.events().get(3).estimatedCost()).isEqualByComparingTo("0.001240");
        assertThat(result.toString()).doesNotContain("please edit").doesNotContain("Tests are passing");
    }

    @Test
    void canRevealPromptsAndFilePathsWhenExplicitlyRequested() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"open ../application-build-resources/AgentInsight/specs/170-agent-session-replay/spec.md"}}
            """);

        var result = builder.build("session-1", rollout.toString(), "repo", "main", "unknown", new ReplayRedactionOptions(true, false, false, true));

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().contentPreview()).contains("open ../application-build-resources/AgentInsight/specs/170-agent-session-replay/spec.md");
        assertThat(result.events().getFirst().filePath()).isEqualTo("../application-build-resources/AgentInsight/specs/170-agent-session-replay/spec.md");
    }

    @Test
    void continuesAfterMalformedLines() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"hello"}}
            {broken}
            {"type":"tool_call","payload":{"tool_name":"search","status":"completed"}}
            """);

        var result = builder.build("session-1", rollout.toString(), "repo", "main", "unknown", ReplayRedactionOptions.defaults());

        assertThat(result.events()).hasSize(2);
        assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("malformed_rollout_lines");
    }

    @Test
    void reportsMissingRolloutPath() {
        var result = builder.build("session-1", null, "repo", "main", "unknown", ReplayRedactionOptions.defaults());

        assertThat(result.events()).isEmpty();
        assertThat(result.rolloutPathPresent()).isFalse();
        assertThat(result.warnings()).containsExactly("rollout_path_missing");
    }

    @Test
    void handlesLargeSessionDeterministically() throws Exception {
        Path rollout = tempDir.resolve("large.jsonl");
        StringBuilder lines = new StringBuilder();
        for (int index = 0; index < 500; index++) {
            lines.append("{\"type\":\"message\",\"payload\":{\"role\":\"assistant\",\"content\":\"event ").append(index).append("\"}}\n");
        }
        Files.writeString(rollout, lines.toString());

        var result = builder.build("session-1", rollout.toString(), "repo", "main", "unknown", ReplayRedactionOptions.defaults());

        assertThat(result.events()).hasSize(500);
        assertThat(result.events().getFirst().sequence()).isEqualTo(1);
        assertThat(result.events().getLast().sequence()).isEqualTo(500);
    }
}
