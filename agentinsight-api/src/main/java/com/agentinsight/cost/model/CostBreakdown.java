package com.agentinsight.cost.model;

import java.math.BigDecimal;

public record CostBreakdown(
    BigDecimal inputCost,
    BigDecimal cachedInputCost,
    BigDecimal outputCost,
    BigDecimal totalCost,
    BigDecimal cacheSavings
) {}
