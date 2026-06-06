package com.agentinsight.usageoptimization.api;

import com.agentinsight.usageoptimization.model.OptimizationSession;
import com.agentinsight.usageoptimization.model.OptimizationWarning;
import com.agentinsight.usageoptimization.model.UsageOptimizationDaily;
import com.agentinsight.usageoptimization.model.UsageOptimizationSummary;
import com.agentinsight.usageoptimization.service.UsageOptimizationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage-optimization")
@CrossOrigin(origins = "*")
public class UsageOptimizationController {
    private final UsageOptimizationService service;

    public UsageOptimizationController(UsageOptimizationService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public UsageOptimizationSummary summary(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String severity
    ) {
        return service.summary(repositoryId, model, day, status, category, severity);
    }

    @GetMapping("/daily")
    public List<UsageOptimizationDaily> daily(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String severity
    ) {
        return service.daily(repositoryId, model, status, category, severity);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<OptimizationSession> session(@PathVariable String sessionId) {
        return service.session(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<UsageOptimizationSummary> repository(@PathVariable String repositoryId) {
        return service.repositorySummary(repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/warnings")
    public List<OptimizationWarning> warnings(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String severity
    ) {
        return service.warnings(repositoryId, model, day, status, category, severity);
    }
}
