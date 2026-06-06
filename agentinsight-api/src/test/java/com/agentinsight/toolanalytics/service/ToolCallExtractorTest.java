package com.agentinsight.toolanalytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCallExtractorTest {
    @TempDir
    Path tempDir;

    private final ToolCallExtractor extractor = new ToolCallExtractor();

    @Test
    void normalizesToolLifecycleEventsWithoutRawPayloads() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"tool_call_started","payload":{"tool_call_id":"call-1","tool_name":"exec","status":"started","arguments":{"cmd":"secret command"},"timestamp":"2026-06-04T10:00:00Z"}}
            {"type":"tool_call_result","payload":{"tool_call_id":"call-1","tool_name":"exec","status":"completed","result":"sensitive output","timestamp":"2026-06-04T10:00:02Z"}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.malformedLines()).isZero();
        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.toolCalls().getFirst().toolName()).isEqualTo("exec");
        assertThat(result.toolCalls().getFirst().argumentsHash()).isNotBlank();
        assertThat(result.toString()).doesNotContain("secret command").doesNotContain("sensitive output");
    }

    @Test
    void continuesAfterMalformedToolEvents() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"tool_call","payload":{"tool_name":"search","status":"started"}}
            {broken}
            {"type":"tool_result","payload":{"tool_name":"search","status":"failed","error":"no match"}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("malformed_rollout_lines");
    }

    @Test
    void reportsMissingRolloutPath() {
        var result = extractor.extract("session-1", null);

        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.rolloutPathPresent()).isFalse();
        assertThat(result.warnings()).containsExactly("rollout_path_missing");
    }
}
