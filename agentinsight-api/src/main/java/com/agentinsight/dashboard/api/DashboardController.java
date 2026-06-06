package com.agentinsight.dashboard.api;

import com.agentinsight.dashboard.model.DashboardResponse;
import com.agentinsight.dashboard.model.ModelStat;
import com.agentinsight.dashboard.model.RepositoryStat;
import com.agentinsight.dashboard.service.DashboardService;
import com.agentinsight.session.model.SessionSummary;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/sessions")
    public List<SessionSummary> sessions() {
        return dashboardService.sessions();
    }

    @GetMapping("/repositories")
    public List<RepositoryStat> repositories() {
        return dashboardService.repositoryStats();
    }

    @GetMapping("/models")
    public List<ModelStat> models() {
        return dashboardService.modelStats();
    }
}
