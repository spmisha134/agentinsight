package com.agentinsight.liveusage.api;

import com.agentinsight.liveusage.model.LiveSessionState;
import com.agentinsight.liveusage.model.LiveUsageSummary;
import com.agentinsight.liveusage.service.LiveUsageMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/live-usage-monitoring")
@CrossOrigin(origins = "*")
public class LiveUsageMonitoringController {
    private final LiveUsageMonitoringService service;

    public LiveUsageMonitoringController(LiveUsageMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public LiveUsageSummary summary() {
        return service.summary();
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<LiveSessionState> session(@PathVariable String sessionId) {
        return service.session(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
