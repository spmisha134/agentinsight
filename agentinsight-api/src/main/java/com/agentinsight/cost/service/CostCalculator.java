package com.agentinsight.cost.service;

import com.agentinsight.cost.model.CostBreakdown;
import com.agentinsight.cost.model.Pricing;
import com.agentinsight.session.model.TokenUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class CostCalculator {
    public CostBreakdown calculate(TokenUsage usage, Pricing pricing) {
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        BigDecimal uncachedInput = BigDecimal.valueOf(Math.max(0, usage.inputTokens() - usage.cachedInputTokens()));
        BigDecimal cachedInput = BigDecimal.valueOf(usage.cachedInputTokens());
        BigDecimal output = BigDecimal.valueOf(usage.outputTokens() + usage.reasoningOutputTokens());

        BigDecimal inputCost = uncachedInput.divide(million, 12, RoundingMode.HALF_UP).multiply(pricing.inputPerMillion());
        BigDecimal cachedCost = cachedInput.divide(million, 12, RoundingMode.HALF_UP).multiply(pricing.cachedInputPerMillion());
        BigDecimal outputCost = output.divide(million, 12, RoundingMode.HALF_UP).multiply(pricing.outputPerMillion());
        BigDecimal total = inputCost.add(cachedCost).add(outputCost);
        BigDecimal withoutCache = BigDecimal.valueOf(usage.inputTokens()).divide(million, 12, RoundingMode.HALF_UP).multiply(pricing.inputPerMillion());
        BigDecimal cacheSavings = withoutCache.subtract(inputCost.add(cachedCost));

        return new CostBreakdown(scale(inputCost), scale(cachedCost), scale(outputCost), scale(total), scale(cacheSavings));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
