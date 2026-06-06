package com.agentinsight.cost.model;

import java.math.BigDecimal;

public record Pricing(BigDecimal inputPerMillion, BigDecimal cachedInputPerMillion, BigDecimal outputPerMillion) {
    public static Pricing zero() {
        return new Pricing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
