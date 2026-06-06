package com.agentinsight.contextmemory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextExtractorTest {
    @TempDir
    Path tempDir;

    private final ContextExtractor extractor = new ContextExtractor();

    @Test
    void extractsContextSegmentsAndMemoryReferences() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"Read AGENTS.md and ../application-build-resources/AgentInsight/specs/130-context-memory-analytics/spec.md before implementing."}}
            {"type":"message","payload":{"role":"assistant","content":"Acknowledged."}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.rolloutReadable()).isTrue();
        assertThat(result.malformedLines()).isZero();
        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments().getFirst().role()).isEqualTo("user");
        assertThat(result.segments().getFirst().memoryReferences())
            .extracting("type")
            .contains("repo_instructions", "project_doc");
    }

    @Test
    void continuesAfterMalformedRolloutLines() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"message","payload":{"role":"user","content":"valid context"}}
            {not-json}
            {"type":"message","payload":{"role":"user","content":"valid context again"}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.segments()).hasSize(2);
        assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("malformed_rollout_lines");
    }

    @Test
    void reportsMissingRolloutWithoutThrowing() {
        var result = extractor.extract("session-1", null);

        assertThat(result.segments()).isEmpty();
        assertThat(result.rolloutPathPresent()).isFalse();
        assertThat(result.warnings()).containsExactly("rollout_path_missing");
    }
}
