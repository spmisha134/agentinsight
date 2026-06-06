package com.agentinsight.contextmemory.api;

import com.agentinsight.contextmemory.model.ContextAnalyticsSummary;
import com.agentinsight.contextmemory.model.ContextDailyAnalytics;
import com.agentinsight.contextmemory.model.ContextSessionAnalytics;
import com.agentinsight.contextmemory.model.ContextWarning;
import com.agentinsight.contextmemory.service.ContextMemoryAnalyticsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/context-memory-analytics")
@CrossOrigin(origins = "*")
public class ContextMemoryAnalyticsController {
    private final ContextMemoryAnalyticsService service;

    public ContextMemoryAnalyticsController(ContextMemoryAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ContextAnalyticsSummary summary(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status
    ) {
        return service.summary(repositoryId, model, day, status);
    }

    @GetMapping("/daily")
    public List<ContextDailyAnalytics> daily(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String status
    ) {
        return service.daily(repositoryId, model, status);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ContextSessionAnalytics> session(@PathVariable String sessionId) {
        return service.session(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<ContextAnalyticsSummary> repository(@PathVariable String repositoryId) {
        return service.repositorySummary(repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/warnings")
    public List<ContextWarning> warnings(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status
    ) {
        return service.warnings(repositoryId, model, day, status);
    }
}
