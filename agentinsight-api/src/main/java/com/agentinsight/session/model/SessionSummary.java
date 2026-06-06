package com.agentinsight.session.model;

import com.agentinsight.cost.model.CostBreakdown;

public record SessionSummary(
    String id,
    String title,
    String cwd,
    String repositoryUrl,
    String branch,
    String model,
    long createdAtMs,
    long updatedAtMs,
    long tokensUsed,
    CostBreakdown cost
) {}
