package com.agentinsight.agentsessionreplay.api;

import com.agentinsight.agentsessionreplay.model.ReplayDailyAnalytics;
import com.agentinsight.agentsessionreplay.model.ReplaySession;
import com.agentinsight.agentsessionreplay.model.ReplaySummary;
import com.agentinsight.agentsessionreplay.model.ReplayWarning;
import com.agentinsight.agentsessionreplay.service.AgentSessionReplayService;
import com.agentinsight.agentsessionreplay.service.ReplayRedactionOptions;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-session-replay")
@CrossOrigin(origins = "*")
public class AgentSessionReplayController {
    private final AgentSessionReplayService service;

    public AgentSessionReplayController(AgentSessionReplayService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ReplaySummary summary(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String eventType
    ) {
        return service.summary(repositoryId, model, day, status, eventType);
    }

    @GetMapping("/daily")
    public List<ReplayDailyAnalytics> daily(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String eventType
    ) {
        return service.daily(repositoryId, model, status, eventType);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ReplaySession> session(
        @PathVariable String sessionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) String eventType,
        @RequestParam(defaultValue = "false") boolean showPrompts,
        @RequestParam(defaultValue = "false") boolean showOutputs,
        @RequestParam(defaultValue = "false") boolean showCommandOutput,
        @RequestParam(defaultValue = "false") boolean showFilePaths
    ) {
        ReplayRedactionOptions redaction = new ReplayRedactionOptions(showPrompts, showOutputs, showCommandOutput, showFilePaths);
        return service.session(sessionId, page, size, eventType, redaction)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<ReplaySummary> repository(@PathVariable String repositoryId) {
        return service.repositorySummary(repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/warnings")
    public List<ReplayWarning> warnings(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String eventType
    ) {
        return service.warnings(repositoryId, model, day, status, eventType);
    }
}
