package com.agentinsight.cost.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentinsight.cost.model.Pricing;
import com.agentinsight.session.model.TokenUsage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {
    private final CostCalculator calculator = new CostCalculator();

    @Test
    void calculatesCostsFromConfiguredTokenUsage() {
        TokenUsage usage = new TokenUsage(1_000_000, 250_000, 100_000, 50_000, 1_150_000);
        Pricing pricing = new Pricing(BigDecimal.valueOf(2), BigDecimal.valueOf(0.5), BigDecimal.valueOf(10));

        var cost = calculator.calculate(usage, pricing);

        assertThat(cost.inputCost()).isEqualByComparingTo("1.500000");
        assertThat(cost.cachedInputCost()).isEqualByComparingTo("0.125000");
        assertThat(cost.outputCost()).isEqualByComparingTo("1.500000");
        assertThat(cost.totalCost()).isEqualByComparingTo("3.125000");
        assertThat(cost.cacheSavings()).isEqualByComparingTo("0.375000");
    }
}
