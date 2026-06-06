package com.agentinsight.liveusage.service;

import com.agentinsight.liveusage.model.LiveSessionState;
import com.agentinsight.liveusage.model.LiveSessionStatus;
import com.agentinsight.liveusage.model.LiveUsageSummary;
import com.agentinsight.source.sqlite.CodexStateRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LiveUsageMonitoringService {
    private final CodexStateRepository repository;
    private final LiveSessionStateClassifier classifier;

    public LiveUsageMonitoringService(CodexStateRepository repository, LiveSessionStateClassifier classifier) {
        this.repository = repository;
        this.classifier = classifier;
    }

    public LiveUsageSummary summary() {
        Instant generatedAt = Instant.now();
        List<LiveSessionState> sessions = repository.findThreads().stream()
            .map(thread -> classifier.classify(thread, generatedAt))
            .sorted(Comparator.comparingLong(LiveSessionState::latestActivityMs).reversed())
            .toList();
        return new LiveUsageSummary(
            generatedAt,
            dataCompleteness(sessions),
            sessions.size(),
            count(sessions, LiveSessionStatus.ACTIVE),
            count(sessions, LiveSessionStatus.STALE),
            count(sessions, LiveSessionStatus.INACTIVE),
            count(sessions, LiveSessionStatus.UNKNOWN),
            sessions.stream().flatMap(session -> session.warnings().stream()).distinct().toList(),
            sessions
        );
    }

    public Optional<LiveSessionState> session(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return summary().sessions().stream()
            .filter(session -> session.sessionId().equals(sessionId))
            .findFirst();
    }

    private int count(List<LiveSessionState> sessions, LiveSessionStatus status) {
        return (int) sessions.stream().filter(session -> session.status() == status).count();
    }

    private String dataCompleteness(List<LiveSessionState> sessions) {
        if (sessions.isEmpty()) {
            return "not_available";
        }
        if (sessions.stream().allMatch(session -> "exact".equals(session.dataCompleteness()))) {
            return "exact";
        }
        return "partial";
    }
}
