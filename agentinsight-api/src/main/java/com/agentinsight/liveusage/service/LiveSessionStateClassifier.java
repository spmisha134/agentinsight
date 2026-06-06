package com.agentinsight.liveusage.service;

import com.agentinsight.liveusage.model.LiveSessionState;
import com.agentinsight.liveusage.model.LiveSessionStatus;
import com.agentinsight.source.sqlite.CodexStateRepository.RawThread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LiveSessionStateClassifier {
    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);
    private static final Duration STALE_WINDOW = Duration.ofMinutes(30);

    public LiveSessionState classify(RawThread thread, Instant generatedAt) {
        FileEvidence rollout = rolloutEvidence(thread.rolloutPath());
        long latestActivityMs = Math.max(thread.updatedAtMs(), rollout.modifiedAtMs() == null ? 0L : rollout.modifiedAtMs());
        Long readFreshnessMs = latestActivityMs > 0 ? Math.max(0L, generatedAt.toEpochMilli() - latestActivityMs) : null;
        List<String> warnings = warnings(thread, rollout);
        LiveSessionStatus status = status(readFreshnessMs, rollout);
        String dataCompleteness = dataCompleteness(rollout);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("activeWindowMs", ACTIVE_WINDOW.toMillis());
        evidence.put("staleWindowMs", STALE_WINDOW.toMillis());
        evidence.put("threadUpdatedAtMs", thread.updatedAtMs());
        evidence.put("rolloutPathPresent", thread.rolloutPath() != null && !thread.rolloutPath().isBlank());
        evidence.put("rolloutReadable", rollout.readable());

        return new LiveSessionState(
            thread.id(),
            thread.title(),
            thread.cwd(),
            thread.repositoryUrl(),
            thread.branch(),
            blankToUnknown(thread.model()),
            status,
            statusReason(status, readFreshnessMs, rollout),
            confidence(rollout),
            latestActivityMs,
            thread.updatedAtMs(),
            rollout.modifiedAtMs(),
            rollout.sizeBytes(),
            readFreshnessMs,
            dataCompleteness,
            warnings,
            evidence
        );
    }

    private LiveSessionStatus status(Long readFreshnessMs, FileEvidence rollout) {
        if (readFreshnessMs == null) {
            return LiveSessionStatus.UNKNOWN;
        }
        if (!rollout.available()) {
            return LiveSessionStatus.UNKNOWN;
        }
        if (readFreshnessMs <= ACTIVE_WINDOW.toMillis()) {
            return LiveSessionStatus.ACTIVE;
        }
        if (readFreshnessMs <= STALE_WINDOW.toMillis()) {
            return LiveSessionStatus.STALE;
        }
        return LiveSessionStatus.INACTIVE;
    }

    private String statusReason(LiveSessionStatus status, Long readFreshnessMs, FileEvidence rollout) {
        if (!rollout.available()) {
            return "rollout_not_available";
        }
        if (readFreshnessMs == null) {
            return "latest_activity_not_available";
        }
        return switch (status) {
            case ACTIVE -> "latest_activity_within_active_window";
            case STALE -> "latest_activity_outside_active_window";
            case INACTIVE -> "latest_activity_outside_stale_window";
            case UNKNOWN -> "insufficient_evidence";
        };
    }

    private String confidence(FileEvidence rollout) {
        if (!rollout.available()) {
            return "low";
        }
        return "medium";
    }

    private String dataCompleteness(FileEvidence rollout) {
        if (!rollout.pathPresent()) {
            return "not_available";
        }
        if (!rollout.available()) {
            return "partial";
        }
        return "exact";
    }

    private List<String> warnings(RawThread thread, FileEvidence rollout) {
        if (thread.rolloutPath() == null || thread.rolloutPath().isBlank()) {
            return List.of("rollout_path_missing");
        }
        if (!rollout.exists()) {
            return List.of("rollout_file_missing");
        }
        if (!rollout.readable()) {
            return List.of("rollout_file_not_readable");
        }
        return List.of();
    }

    private FileEvidence rolloutEvidence(String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return new FileEvidence(false, false, false, null, null);
        }
        try {
            Path path = Path.of(rolloutPath);
            boolean exists = Files.exists(path);
            boolean readable = exists && Files.isReadable(path);
            Long modifiedAt = readable ? Files.getLastModifiedTime(path).toMillis() : null;
            Long sizeBytes = readable ? Files.size(path) : null;
            return new FileEvidence(true, exists, readable, modifiedAt, sizeBytes);
        } catch (Exception e) {
            return new FileEvidence(true, false, false, null, null);
        }
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private record FileEvidence(boolean pathPresent, boolean exists, boolean readable, Long modifiedAtMs, Long sizeBytes) {
        boolean available() {
            return pathPresent && exists && readable;
        }
    }
}
