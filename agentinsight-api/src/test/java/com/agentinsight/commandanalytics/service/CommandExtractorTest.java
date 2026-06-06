package com.agentinsight.commandanalytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandExtractorTest {
    @TempDir
    Path tempDir;

    private final CommandExtractor extractor = new CommandExtractor(new CommandClassifier());

    @Test
    void extractsCommandLifecycleWithoutRawOutput() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"tool_call_started","payload":{"tool_call_id":"call-1","tool_name":"exec","status":"started","arguments":{"cmd":"./gradlew test TOKEN=secret","cwd":"/repo"},"timestamp":"2026-06-04T10:00:00Z"}}
            {"type":"tool_call_result","payload":{"tool_call_id":"call-1","tool_name":"exec","status":"completed","result":{"exit_code":0,"stdout":"sensitive test output","stderr":""},"timestamp":"2026-06-04T10:00:03Z"}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.malformedLines()).isZero();
        assertThat(result.commands()).hasSize(1);
        var command = result.commands().getFirst();
        assertThat(command.commandExecutable()).isEqualTo("./gradlew");
        assertThat(command.category()).isEqualTo("test");
        assertThat(command.status()).isEqualTo("succeeded");
        assertThat(command.durationMs()).isEqualTo(3000L);
        assertThat(command.stdoutSizeBytes()).isGreaterThan(0);
        assertThat(command.toString()).doesNotContain("sensitive test output");
        assertThat(command.commandPreview()).contains("TOKEN=<redacted>");
    }

    @Test
    void continuesAfterMalformedCommandEvents() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"tool_call","payload":{"tool_name":"exec","arguments":{"cmd":"npm test"},"status":"started"}}
            {broken}
            {"type":"tool_call","payload":{"tool_name":"exec","arguments":{"cmd":"git status"},"status":"completed","exit_code":0}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.commands()).hasSize(2);
        assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("malformed_rollout_lines");
    }

    @Test
    void reportsMissingRolloutPath() {
        var result = extractor.extract("session-1", null);

        assertThat(result.commands()).isEmpty();
        assertThat(result.rolloutPathPresent()).isFalse();
        assertThat(result.warnings()).containsExactly("rollout_path_missing");
    }
}
