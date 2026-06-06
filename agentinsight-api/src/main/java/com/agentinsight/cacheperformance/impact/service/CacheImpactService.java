package com.agentinsight.cacheperformance.impact.service;

import com.agentinsight.cacheperformance.impact.model.CacheImpactCompleteness;
import com.agentinsight.cacheperformance.impact.model.CacheImpactDaily;
import com.agentinsight.cacheperformance.impact.model.CacheImpactModel;
import com.agentinsight.cacheperformance.impact.model.CacheImpactRecommendation;
import com.agentinsight.cacheperformance.impact.model.CacheImpactRepository;
import com.agentinsight.cacheperformance.impact.model.CacheImpactSession;
import com.agentinsight.cacheperformance.impact.model.CacheImpactSessionPage;
import com.agentinsight.cacheperformance.impact.model.CacheImpactSummary;
import com.agentinsight.cacheperformance.impact.model.CacheTopSavings;
import com.agentinsight.cacheperformance.model.CacheSessionAnalytics;
import com.agentinsight.cacheperformance.service.CachePerformanceService;
import com.agentinsight.cost.model.Pricing;
import com.agentinsight.cost.service.PricingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CacheImpactService {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final String CURRENCY = "USD";
    private static final String PRICING_PROFILE_ID = "default";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final CachePerformanceService cachePerformanceService;
    private final PricingService pricingService;

    public CacheImpactService(CachePerformanceService cachePerformanceService, PricingService pricingService) {
        this.cachePerformanceService = cachePerformanceService;
        this.pricingService = pricingService;
    }

    public CacheImpactSummary summary(String from, String to, String repository, String model) {
        Aggregate aggregate = aggregate(impacts(from, to, repository, model));
        return new CacheImpactSummary(
            CURRENCY,
            PRICING_PROFILE_ID,
            aggregate.actualCost(),
            aggregate.withoutCacheCost(),
            aggregate.savings(),
            savingsPercent(aggregate.savings(), aggregate.withoutCacheCost()),
            cacheEfficiency(aggregate.cachedInputTokens(), aggregate.inputTokens()),
            aggregate.inputTokens(),
            aggregate.cachedInputTokens(),
            aggregate.uncachedInputTokens(),
            aggregate.outputTokens(),
            0L,
            null,
            aggregate.completeness(),
            Instant.now()
        );
    }

    public List<CacheImpactDaily> daily(String from, String to, String repository, String model) {
        return impacts(from, to, repository, model).stream()
            .collect(Collectors.groupingBy(impact -> day(impact.session().updatedAtMs()), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> {
                Aggregate aggregate = aggregate(entry.getValue());
                return new CacheImpactDaily(
                    entry.getKey(),
                    aggregate.actualCost(),
                    aggregate.withoutCacheCost(),
                    aggregate.savings(),
                    savingsPercent(aggregate.savings(), aggregate.withoutCacheCost()),
                    cacheEfficiency(aggregate.cachedInputTokens(), aggregate.inputTokens()),
                    aggregate.cachedInputTokens(),
                    aggregate.uncachedInputTokens()
                );
            })
            .sorted(Comparator.comparing(CacheImpactDaily::day))
            .toList();
    }

    public List<CacheImpactRepository> repositories(String sort, String direction, Integer limit) {
        return impacts(null, null, null, null).stream()
            .collect(Collectors.groupingBy(impact -> impact.session().repositoryId(), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> repositoryImpact(entry.getKey(), entry.getValue()))
            .sorted(repositoryComparator(sort, direction))
            .limit(normalizedLimit(limit))
            .toList();
    }

    public List<CacheImpactModel> models() {
        return impacts(null, null, null, null).stream()
            .collect(Collectors.groupingBy(impact -> impact.session().model(), LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .map(entry -> modelImpact(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(CacheImpactModel::savings).reversed())
            .toList();
    }

    public CacheImpactSessionPage sessions(
        String sort,
        String direction,
        String repository,
        String model,
        Double minCacheEfficiency,
        Double maxCacheEfficiency,
        Integer limit,
        Integer offset
    ) {
        int safeLimit = normalizedLimit(limit);
        int safeOffset = Math.max(0, offset == null ? 0 : offset);
        List<CacheImpactSession> filtered = impacts(null, null, repository, model).stream()
            .map(this::sessionImpact)
            .filter(session -> minCacheEfficiency == null || session.cacheEfficiency() >= minCacheEfficiency)
            .filter(session -> maxCacheEfficiency == null || session.cacheEfficiency() <= maxCacheEfficiency)
            .sorted(sessionComparator(sort, direction))
            .toList();
        List<CacheImpactSession> items = filtered.stream()
            .skip(safeOffset)
            .limit(safeLimit)
            .toList();
        return new CacheImpactSessionPage(items, filtered.size(), safeLimit, safeOffset);
    }

    public CacheTopSavings topSavings(Integer limit) {
        int safeLimit = normalizedLimit(limit);
        return new CacheTopSavings(
            repositories("savings", "desc", safeLimit),
            models().stream().limit(safeLimit).toList(),
            sessions("savings", "desc", null, null, null, null, safeLimit, 0).items()
        );
    }

    public List<CacheImpactRecommendation> recommendations() {
        List<Impact> expensiveLowCacheSessions = impacts(null, null, null, null).stream()
            .filter(impact -> impact.session().metrics().inputTokens() >= 1_000L)
            .filter(impact -> cacheEfficiency(impact.session().metrics().cachedInputTokens(), impact.session().metrics().inputTokens()) < 50.0)
            .sorted(Comparator.comparing(Impact::withoutCacheCost).reversed())
            .limit(10)
            .toList();
        if (expensiveLowCacheSessions.isEmpty()) {
            return List.of();
        }

        BigDecimal impactCost = expensiveLowCacheSessions.stream()
            .map(Impact::withoutCacheCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long impactTokens = expensiveLowCacheSessions.stream()
            .mapToLong(impact -> impact.session().metrics().uncachedInputTokens())
            .sum();
        List<String> sessionIds = expensiveLowCacheSessions.stream()
            .map(impact -> impact.session().sessionId())
            .toList();
        List<String> repositoryKeys = expensiveLowCacheSessions.stream()
            .map(impact -> impact.session().repositoryId())
            .distinct()
            .toList();

        return List.of(new CacheImpactRecommendation(
            "low-cache-expensive-sessions",
            "LOW_CACHE_EFFICIENCY",
            impactCost.compareTo(BigDecimal.ONE) >= 0 ? "HIGH" : "MEDIUM",
            "Low cache efficiency in expensive sessions",
            "High-input sessions are spending most input tokens at uncached pricing.",
            "Keep stable instructions and reusable project context at the beginning of the context.",
            scale(impactCost),
            impactTokens,
            sessionIds,
            repositoryKeys
        ));
    }

    private List<Impact> impacts(String from, String to, String repository, String model) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        return cachePerformanceService.sessions(repository, model, null, null).stream()
            .filter(session -> withinDateRange(session.updatedAtMs(), fromDate, toDate))
            .map(this::impact)
            .toList();
    }

    private Impact impact(CacheSessionAnalytics session) {
        Pricing pricing = pricingService.pricingFor(session.model());
        long inputTokens = session.metrics().inputTokens();
        long cachedInputTokens = session.metrics().cachedInputTokens();
        long uncachedInputTokens = Math.max(0L, session.metrics().uncachedInputTokens());
        long outputTokens = session.metrics().outputTokens();

        BigDecimal actualCost = tokenCost(uncachedInputTokens, pricing.inputPerMillion())
            .add(tokenCost(cachedInputTokens, pricing.cachedInputPerMillion()))
            .add(tokenCost(outputTokens, pricing.outputPerMillion()));
        BigDecimal withoutCacheCost = tokenCost(inputTokens, pricing.inputPerMillion())
            .add(tokenCost(outputTokens, pricing.outputPerMillion()));
        BigDecimal savings = withoutCacheCost.subtract(actualCost);
        CacheImpactCompleteness completeness = completeness(session, pricing);
        if (savings.signum() < 0) {
            savings = BigDecimal.ZERO;
            completeness = CacheImpactCompleteness.PARTIAL;
        }

        return new Impact(session, scale(actualCost), scale(withoutCacheCost), scale(savings), completeness);
    }

    private CacheImpactSession sessionImpact(Impact impact) {
        CacheSessionAnalytics session = impact.session();
        return new CacheImpactSession(
            session.sessionId(),
            session.title(),
            repositoryLabel(session.repositoryId()),
            session.model(),
            impact.actualCost(),
            impact.withoutCacheCost(),
            impact.savings(),
            savingsPercent(impact.savings(), impact.withoutCacheCost()),
            cacheEfficiency(session.metrics().cachedInputTokens(), session.metrics().inputTokens()),
            session.metrics().inputTokens(),
            session.metrics().cachedInputTokens(),
            session.metrics().uncachedInputTokens(),
            session.updatedAtMs()
        );
    }

    private CacheImpactRepository repositoryImpact(String repositoryKey, List<Impact> impacts) {
        Aggregate aggregate = aggregate(impacts);
        return new CacheImpactRepository(
            repositoryKey,
            repositoryLabel(repositoryKey),
            impacts.size(),
            aggregate.actualCost(),
            aggregate.withoutCacheCost(),
            aggregate.savings(),
            savingsPercent(aggregate.savings(), aggregate.withoutCacheCost()),
            cacheEfficiency(aggregate.cachedInputTokens(), aggregate.inputTokens()),
            aggregate.cachedInputTokens(),
            aggregate.uncachedInputTokens()
        );
    }

    private CacheImpactModel modelImpact(String model, List<Impact> impacts) {
        Aggregate aggregate = aggregate(impacts);
        return new CacheImpactModel(
            model,
            impacts.size(),
            aggregate.actualCost(),
            aggregate.withoutCacheCost(),
            aggregate.savings(),
            savingsPercent(aggregate.savings(), aggregate.withoutCacheCost()),
            cacheEfficiency(aggregate.cachedInputTokens(), aggregate.inputTokens())
        );
    }

    private Aggregate aggregate(List<Impact> impacts) {
        BigDecimal actualCost = impacts.stream().map(Impact::actualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withoutCacheCost = impacts.stream().map(Impact::withoutCacheCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal savings = impacts.stream().map(Impact::savings).reduce(BigDecimal.ZERO, BigDecimal::add);
        long inputTokens = impacts.stream().mapToLong(impact -> impact.session().metrics().inputTokens()).sum();
        long cachedInputTokens = impacts.stream().mapToLong(impact -> impact.session().metrics().cachedInputTokens()).sum();
        long uncachedInputTokens = impacts.stream().mapToLong(impact -> impact.session().metrics().uncachedInputTokens()).sum();
        long outputTokens = impacts.stream().mapToLong(impact -> impact.session().metrics().outputTokens()).sum();
        CacheImpactCompleteness completeness = aggregateCompleteness(impacts);
        return new Aggregate(
            scale(actualCost),
            scale(withoutCacheCost),
            scale(savings),
            inputTokens,
            cachedInputTokens,
            uncachedInputTokens,
            outputTokens,
            completeness
        );
    }

    private CacheImpactCompleteness aggregateCompleteness(List<Impact> impacts) {
        if (impacts.isEmpty()) {
            return CacheImpactCompleteness.UNAVAILABLE;
        }
        if (impacts.stream().allMatch(impact -> impact.completeness() == CacheImpactCompleteness.EXACT)) {
            return CacheImpactCompleteness.EXACT;
        }
        if (impacts.stream().anyMatch(impact -> impact.completeness() == CacheImpactCompleteness.UNAVAILABLE)) {
            return CacheImpactCompleteness.UNAVAILABLE;
        }
        return CacheImpactCompleteness.PARTIAL;
    }

    private CacheImpactCompleteness completeness(CacheSessionAnalytics session, Pricing pricing) {
        if (isMissingPricing(pricing) || "not_available".equals(session.dataCompleteness())) {
            return CacheImpactCompleteness.UNAVAILABLE;
        }
        if (!"exact".equals(session.dataCompleteness()) || !"exact".equals(session.metrics().metricCompleteness())) {
            return CacheImpactCompleteness.PARTIAL;
        }
        return CacheImpactCompleteness.EXACT;
    }

    private boolean isMissingPricing(Pricing pricing) {
        return pricing.inputPerMillion().signum() == 0
            && pricing.cachedInputPerMillion().signum() == 0
            && pricing.outputPerMillion().signum() == 0;
    }

    private Comparator<CacheImpactRepository> repositoryComparator(String sort, String direction) {
        Comparator<CacheImpactRepository> comparator = switch (sort == null ? "savings" : sort) {
            case "savingsPercent" -> Comparator.comparing(CacheImpactRepository::savingsPercent);
            case "cacheEfficiency" -> Comparator.comparing(CacheImpactRepository::cacheEfficiency);
            case "actualCost" -> Comparator.comparing(CacheImpactRepository::actualCost);
            default -> Comparator.comparing(CacheImpactRepository::savings);
        };
        return descending(direction) ? comparator.reversed() : comparator;
    }

    private Comparator<CacheImpactSession> sessionComparator(String sort, String direction) {
        Comparator<CacheImpactSession> comparator = switch (sort == null ? "savings" : sort) {
            case "savingsPercent" -> Comparator.comparing(CacheImpactSession::savingsPercent);
            case "cacheEfficiency" -> Comparator.comparing(CacheImpactSession::cacheEfficiency);
            case "actualCost" -> Comparator.comparing(CacheImpactSession::actualCost);
            case "withoutCacheCost" -> Comparator.comparing(CacheImpactSession::withoutCacheCost);
            default -> Comparator.comparing(CacheImpactSession::savings);
        };
        return descending(direction) ? comparator.reversed() : comparator;
    }

    private boolean descending(String direction) {
        return direction == null || !"asc".equalsIgnoreCase(direction);
    }

    private int normalizedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean withinDateRange(long epochMs, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (epochMs <= 0L) {
            return false;
        }
        LocalDate day = LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String day(long epochMs) {
        if (epochMs <= 0L) {
            return "unknown";
        }
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC).toString();
    }

    private String repositoryLabel(String repositoryKey) {
        if (repositoryKey == null || repositoryKey.isBlank()) {
            return "unknown";
        }
        String normalized = repositoryKey.replace("git@github.com:", "github.com/").replace(".git", "");
        try {
            return Path.of(normalized).getFileName().toString();
        } catch (Exception e) {
            int slash = normalized.lastIndexOf('/');
            return slash >= 0 ? normalized.substring(slash + 1) : normalized;
        }
    }

    private BigDecimal tokenCost(long tokens, BigDecimal pricePerMillion) {
        if (tokens <= 0L || pricePerMillion.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
            .divide(MILLION, 12, RoundingMode.HALF_UP)
            .multiply(pricePerMillion);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private double savingsPercent(BigDecimal savings, BigDecimal withoutCacheCost) {
        if (withoutCacheCost.signum() <= 0) {
            return 0.0;
        }
        return savings
            .divide(withoutCacheCost, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private double cacheEfficiency(long cachedInputTokens, long inputTokens) {
        if (inputTokens <= 0L) {
            return 0.0;
        }
        return BigDecimal.valueOf(cachedInputTokens)
            .divide(BigDecimal.valueOf(inputTokens), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private record Impact(
        CacheSessionAnalytics session,
        BigDecimal actualCost,
        BigDecimal withoutCacheCost,
        BigDecimal savings,
        CacheImpactCompleteness completeness
    ) {}

    private record Aggregate(
        BigDecimal actualCost,
        BigDecimal withoutCacheCost,
        BigDecimal savings,
        long inputTokens,
        long cachedInputTokens,
        long uncachedInputTokens,
        long outputTokens,
        CacheImpactCompleteness completeness
    ) {}
}
