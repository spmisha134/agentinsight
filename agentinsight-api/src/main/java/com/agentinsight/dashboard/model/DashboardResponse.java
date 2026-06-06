package com.agentinsight.dashboard.model;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
    long totalSessions,
    long totalTokens,
    BigDecimal estimatedCost,
    BigDecimal cacheSavings,
    List<RepositoryStat> topRepositories,
    List<ModelStat> models
) {}
