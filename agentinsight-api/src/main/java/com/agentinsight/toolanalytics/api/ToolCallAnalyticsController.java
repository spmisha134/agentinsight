package com.agentinsight.toolanalytics.api;

import com.agentinsight.toolanalytics.model.ToolAnalyticsSummary;
import com.agentinsight.toolanalytics.model.ToolDailyAnalytics;
import com.agentinsight.toolanalytics.model.ToolSessionAnalytics;
import com.agentinsight.toolanalytics.model.ToolWarning;
import com.agentinsight.toolanalytics.service.ToolCallAnalyticsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool-call-analytics")
@CrossOrigin(origins = "*")
public class ToolCallAnalyticsController {
    private final ToolCallAnalyticsService service;

    public ToolCallAnalyticsController(ToolCallAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ToolAnalyticsSummary summary(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String toolType
    ) {
        return service.summary(repositoryId, model, day, status, toolType);
    }

    @GetMapping("/daily")
    public List<ToolDailyAnalytics> daily(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String toolType
    ) {
        return service.daily(repositoryId, model, status, toolType);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ToolSessionAnalytics> session(@PathVariable String sessionId) {
        return service.session(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<ToolAnalyticsSummary> repository(@PathVariable String repositoryId) {
        return service.repositorySummary(repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/warnings")
    public List<ToolWarning> warnings(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String toolType
    ) {
        return service.warnings(repositoryId, model, day, status, toolType);
    }
}
