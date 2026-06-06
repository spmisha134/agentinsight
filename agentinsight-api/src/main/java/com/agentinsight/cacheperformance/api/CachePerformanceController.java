package com.agentinsight.cacheperformance.api;

import com.agentinsight.cacheperformance.impact.model.CacheImpactDaily;
import com.agentinsight.cacheperformance.impact.model.CacheImpactModel;
import com.agentinsight.cacheperformance.impact.model.CacheImpactRecommendation;
import com.agentinsight.cacheperformance.impact.model.CacheImpactRepository;
import com.agentinsight.cacheperformance.impact.model.CacheImpactSessionPage;
import com.agentinsight.cacheperformance.impact.model.CacheImpactSummary;
import com.agentinsight.cacheperformance.impact.model.CacheTopSavings;
import com.agentinsight.cacheperformance.impact.service.CacheImpactService;
import com.agentinsight.cacheperformance.model.CacheDailyAnalytics;
import com.agentinsight.cacheperformance.model.CachePerformanceSummary;
import com.agentinsight.cacheperformance.model.CacheSessionAnalytics;
import com.agentinsight.cacheperformance.model.CacheWarning;
import com.agentinsight.cacheperformance.service.CachePerformanceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cache-performance")
@CrossOrigin(origins = "*")
public class CachePerformanceController {
    private final CachePerformanceService service;
    private final CacheImpactService impactService;

    public CachePerformanceController(CachePerformanceService service, CacheImpactService impactService) {
        this.service = service;
        this.impactService = impactService;
    }

    @GetMapping("/summary")
    public CachePerformanceSummary summary(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status
    ) {
        return service.summary(repositoryId, model, day, status);
    }

    @GetMapping("/daily")
    public List<CacheDailyAnalytics> daily(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String status
    ) {
        return service.daily(repositoryId, model, status);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<CacheSessionAnalytics> session(@PathVariable String sessionId) {
        return service.session(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<CachePerformanceSummary> repository(@PathVariable String repositoryId) {
        return service.repositorySummary(repositoryId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/warnings")
    public List<CacheWarning> warnings(
        @RequestParam(required = false) String repositoryId,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String status
    ) {
        return service.warnings(repositoryId, model, day, status);
    }

    @GetMapping("/impact/summary")
    public CacheImpactSummary impactSummary(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String repository,
        @RequestParam(required = false) String model
    ) {
        return impactService.summary(from, to, repository, model);
    }

    @GetMapping("/impact/daily")
    public List<CacheImpactDaily> impactDaily(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String repository,
        @RequestParam(required = false) String model
    ) {
        return impactService.daily(from, to, repository, model);
    }

    @GetMapping("/impact/repositories")
    public List<CacheImpactRepository> impactRepositories(
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String direction,
        @RequestParam(required = false) Integer limit
    ) {
        return impactService.repositories(sort, direction, limit);
    }

    @GetMapping("/impact/models")
    public List<CacheImpactModel> impactModels() {
        return impactService.models();
    }

    @GetMapping("/impact/sessions")
    public CacheImpactSessionPage impactSessions(
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String direction,
        @RequestParam(required = false) String repository,
        @RequestParam(required = false) String model,
        @RequestParam(required = false) Double minCacheEfficiency,
        @RequestParam(required = false) Double maxCacheEfficiency,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset
    ) {
        return impactService.sessions(sort, direction, repository, model, minCacheEfficiency, maxCacheEfficiency, limit, offset);
    }

    @GetMapping("/impact/top-savings")
    public CacheTopSavings topSavings(@RequestParam(required = false) Integer limit) {
        return impactService.topSavings(limit);
    }

    @GetMapping("/impact/recommendations")
    public List<CacheImpactRecommendation> recommendations() {
        return impactService.recommendations();
    }
}
