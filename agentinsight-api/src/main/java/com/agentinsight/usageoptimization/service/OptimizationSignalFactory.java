package com.agentinsight.usageoptimization.service;

import com.agentinsight.usageoptimization.model.OptimizationSignal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OptimizationSignalFactory {
    public OptimizationSignal signal(
        String category,
        String severity,
        String title,
        String recommendation,
        long impactTokens,
        BigDecimal impactCost,
        List<String> affectedSessions,
        String repositoryId,
        String model,
        Map<String, Object> evidence
    ) {
        String idSource = category + "|" + title + "|" + repositoryId + "|" + model + "|" + affectedSessions;
        return new OptimizationSignal(
            sha256(idSource).substring(0, 24),
            category,
            severity,
            title,
            recommendation,
            "open",
            Math.max(0L, impactTokens),
            impactCost == null ? BigDecimal.ZERO : impactCost,
            affectedSessions,
            repositoryId,
            model,
            Instant.now(),
            evidence
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash optimization signal", e);
        }
    }
}
