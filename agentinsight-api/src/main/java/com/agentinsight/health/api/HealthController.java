package com.agentinsight.health.api;

import com.agentinsight.health.model.HealthResponse;
import com.agentinsight.health.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthService.health();
        return "UP".equals(response.status())
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(503).body(response);
    }
}
