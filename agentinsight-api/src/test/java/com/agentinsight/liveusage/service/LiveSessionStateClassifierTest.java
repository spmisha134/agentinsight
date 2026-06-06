package com.agentinsight.liveusage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.liveusage.model.LiveSessionStatus;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveSessionStateClassifierTest {
    @TempDir
    Path tempDir;

    private final LiveSessionStateClassifier classifier = new LiveSessionStateClassifier();
    private final Instant generatedAt = Instant.parse("2026-06-04T10:00:00Z");

    @Test
    void classifiesRecentReadableRolloutAsActive() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, "{}\n");
        rollout.toFile().setLastModified(generatedAt.minusSeconds(60).toEpochMilli());

        var state = classifier.classify(thread(rollout.toString(), generatedAt.minusSeconds(120).toEpochMilli()), generatedAt);

        assertThat(state.status()).isEqualTo(LiveSessionStatus.ACTIVE);
        assertThat(state.dataCompleteness()).isEqualTo("exact");
        assertThat(state.confidence()).isEqualTo("medium");
        assertThat(state.warnings()).isEmpty();
    }

    @Test
    void classifiesReadableRolloutOutsideActiveWindowAsStale() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, "{}\n");
        rollout.toFile().setLastModified(generatedAt.minusSeconds(10 * 60).toEpochMilli());

        var state = classifier.classify(thread(rollout.toString(), generatedAt.minusSeconds(15 * 60).toEpochMilli()), generatedAt);

        assertThat(state.status()).isEqualTo(LiveSessionStatus.STALE);
        assertThat(state.statusReason()).isEqualTo("latest_activity_outside_active_window");
    }

    @Test
    void classifiesOldReadableRolloutAsInactive() throws Exception {
        Path rollout = tempDir.resolve("rollout.jsonl");
        Files.writeString(rollout, "{}\n");
        rollout.toFile().setLastModified(generatedAt.minusSeconds(60 * 60).toEpochMilli());

        var state = classifier.classify(thread(rollout.toString(), generatedAt.minusSeconds(60 * 60).toEpochMilli()), generatedAt);

        assertThat(state.status()).isEqualTo(LiveSessionStatus.INACTIVE);
        assertThat(state.statusReason()).isEqualTo("latest_activity_outside_stale_window");
    }

    @Test
    void reportsMissingRolloutPathAsUnknownPartialData() {
        var state = classifier.classify(thread(null, generatedAt.minusSeconds(60).toEpochMilli()), generatedAt);

        assertThat(state.status()).isEqualTo(LiveSessionStatus.UNKNOWN);
        assertThat(state.dataCompleteness()).isEqualTo("not_available");
        assertThat(state.confidence()).isEqualTo("low");
        assertThat(state.warnings()).containsExactly("rollout_path_missing");
    }

    @Test
    void reportsMissingRolloutFileAsUnknownPartialData() {
        var state = classifier.classify(thread(tempDir.resolve("missing.jsonl").toString(), generatedAt.minusSeconds(60).toEpochMilli()), generatedAt);

        assertThat(state.status()).isEqualTo(LiveSessionStatus.UNKNOWN);
        assertThat(state.dataCompleteness()).isEqualTo("partial");
        assertThat(state.warnings()).containsExactly("rollout_file_missing");
    }

    private RawThread thread(String rolloutPath, long updatedAtMs) {
        return new RawThread(
            "session-1",
            rolloutPath,
            tempDir.toString(),
            "Local Codex session",
            null,
            null,
            "gpt-5",
            generatedAt.minusSeconds(3600).toEpochMilli(),
            updatedAtMs,
            0
        );
    }
}
