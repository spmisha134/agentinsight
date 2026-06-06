package com.agentinsight.cacheperformance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheEventExtractorTest {
    @TempDir
    Path tempDir;

    private final CacheEventExtractor extractor = new CacheEventExtractor();

    @Test
    void extractsCachedInputTokensFromTokenUsageEvents() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"type":"response","payload":{"info":{"total_token_usage":{"input_tokens":1000,"cached_input_tokens":250,"output_tokens":120,"reasoning_output_tokens":30,"total_tokens":1150}}}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.malformedLines()).isZero();
        assertThat(result.tokenEvents()).hasSize(1);
        assertThat(result.tokenEvents().getFirst().usage().cachedInputTokens()).isEqualTo(250);
        assertThat(result.toString()).doesNotContain("response");
    }

    @Test
    void continuesAfterMalformedLines() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, """
            {"payload":{"info":{"total_token_usage":{"input_tokens":1000,"cached_input_tokens":100,"total_tokens":1000}}}}
            {broken}
            {"payload":{"info":{"total_token_usage":{"input_tokens":2000,"cached_input_tokens":500,"total_tokens":2000}}}}
            """);

        var result = extractor.extract("session-1", rollout.toString());

        assertThat(result.tokenEvents()).hasSize(2);
        assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("malformed_rollout_lines");
    }

    @Test
    void reportsMissingRolloutPath() {
        var result = extractor.extract("session-1", null);

        assertThat(result.tokenEvents()).isEmpty();
        assertThat(result.rolloutPathPresent()).isFalse();
        assertThat(result.warnings()).containsExactly("rollout_path_missing");
    }
}
