package com.agentinsight.commandanalytics.api;

import com.agentinsight.commandanalytics.model.CommandAnalyticsSummary;
import com.agentinsight.commandanalytics.model.CommandDailyAnalytics;
import com.agentinsight.commandanalytics.model.CommandSessionAnalytics;
import com.agentinsight.commandanalytics.model.CommandWarning;
import com.agentinsight.commandanalytics.service.CommandAnalyticsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/command-analytics")
@CrossOrigin(origins = "*")
public class CommandAnalyticsController {
    private final CommandAnalyticsService service;

    public CommandAnalyticsController(CommandAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public CommandAnalyticsSummary summary(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category
    ) {
        return service.summary(repositoryId, model, day, status, category);
    }

    @GetMapping("/daily")
    public List<CommandDailyAnalytics> daily(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category
    ) {
        return service.daily(repositoryId, model, status, category);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<CommandSessionAnalytics> session(@PathVariable String sessionId) {
        return service.session(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<CommandAnalyticsSummary> repository(@PathVariable String repositoryId) {
        return service.repositorySummary(repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/warnings")
    public List<CommandWarning> warnings(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category
    ) {
        return service.warnings(repositoryId, model, day, status, category);
    }
}
