package com.agentinsight.dashboard.service;

import com.agentinsight.cost.model.CostBreakdown;
import com.agentinsight.cost.service.CostCalculator;
import com.agentinsight.cost.service.PricingService;
import com.agentinsight.dashboard.model.DashboardResponse;
import com.agentinsight.dashboard.model.ModelStat;
import com.agentinsight.dashboard.model.RepositoryStat;
import com.agentinsight.importpipeline.parser.RolloutParser;
import com.agentinsight.provider.service.ProviderService;
import com.agentinsight.session.model.SessionSummary;
import com.agentinsight.session.model.TokenUsage;
import com.agentinsight.source.sqlite.CodexStateRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final CodexStateRepository repository;
    private final ProviderService providerService;
    private final RolloutParser rolloutParser;
    private final CostCalculator costCalculator;
    private final PricingService pricingService;

    public DashboardService(CodexStateRepository repository, ProviderService providerService, RolloutParser rolloutParser, CostCalculator costCalculator, PricingService pricingService) {
        this.repository = repository;
        this.providerService = providerService;
        this.rolloutParser = rolloutParser;
        this.costCalculator = costCalculator;
        this.pricingService = pricingService;
    }

    public List<SessionSummary> sessions() {
        List<CodexStateRepository.RawThread> threads = providerService.activeProvider().isPresent()
            ? providerService.activeThreads()
            : repository.findThreads();
        return threads.stream().map(this::toSummary).toList();
    }

    public DashboardResponse dashboard() {
        List<SessionSummary> sessions = sessions();
        BigDecimal totalCost = sessions.stream().map(s -> s.cost().totalCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal savings = sessions.stream().map(s -> s.cost().cacheSavings()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalTokens = sessions.stream().mapToLong(SessionSummary::tokensUsed).sum();
        List<RepositoryStat> repos = repositoryStats(sessions);
        List<ModelStat> models = modelStats(sessions);
        return new DashboardResponse(sessions.size(), totalTokens, totalCost, savings, repos, models);
    }

    public List<RepositoryStat> repositoryStats() {
        return repositoryStats(sessions());
    }

    public List<ModelStat> modelStats() {
        return modelStats(sessions());
    }

    private SessionSummary toSummary(CodexStateRepository.RawThread row) {
        String model = row.model() == null || row.model().isBlank() ? "unknown" : row.model();
        TokenUsage usage = latestUsage(row.rolloutPath())
            .orElse(new TokenUsage(row.tokensUsed(), 0, 0, 0, row.tokensUsed()));
        CostBreakdown cost = costCalculator.calculate(usage, pricingService.pricingFor(model));
        return new SessionSummary(row.id(), row.title(), row.cwd(), row.repositoryUrl(), row.branch(), model, row.createdAtMs(), row.updatedAtMs(), usage.totalTokens(), cost);
    }

    private java.util.Optional<TokenUsage> latestUsage(String rolloutPath) {
        if (rolloutPath == null || rolloutPath.isBlank()) {
            return java.util.Optional.empty();
        }
        return rolloutParser.latestTokenUsage(Path.of(rolloutPath));
    }

    private List<RepositoryStat> repositoryStats(List<SessionSummary> sessions) {
        Map<String, List<SessionSummary>> grouped = sessions.stream().collect(Collectors.groupingBy(s -> label(s.repositoryUrl(), s.cwd())));
        return grouped.entrySet().stream()
            .map(e -> new RepositoryStat(e.getKey(), e.getValue().size(), e.getValue().stream().mapToLong(SessionSummary::tokensUsed).sum(), e.getValue().stream().map(s -> s.cost().totalCost()).reduce(BigDecimal.ZERO, BigDecimal::add)))
            .sorted(Comparator.comparing(RepositoryStat::cost).reversed())
            .limit(10)
            .toList();
    }

    private List<ModelStat> modelStats(List<SessionSummary> sessions) {
        Map<String, List<SessionSummary>> grouped = sessions.stream().collect(Collectors.groupingBy(SessionSummary::model));
        return grouped.entrySet().stream()
            .map(e -> new ModelStat(e.getKey(), e.getValue().size(), e.getValue().stream().mapToLong(SessionSummary::tokensUsed).sum(), e.getValue().stream().map(s -> s.cost().totalCost()).reduce(BigDecimal.ZERO, BigDecimal::add)))
            .sorted(Comparator.comparing(ModelStat::cost).reversed())
            .toList();
    }

    private String label(String repositoryUrl, String cwd) {
        if (repositoryUrl != null && !repositoryUrl.isBlank()) {
            return repositoryUrl.replace("git@github.com:", "github.com/").replace(".git", "");
        }
        if (cwd == null || cwd.isBlank()) {
            return "unknown";
        }
        return Path.of(cwd).getFileName().toString();
    }
}
