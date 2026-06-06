package com.agentinsight.cost.service;

import com.agentinsight.cost.model.Pricing;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
    private final PricingProperties properties;

    public PricingService(PricingProperties properties) {
        this.properties = properties;
    }

    public Pricing pricingFor(String model) {
        Pricing configured = properties.models().get(model);
        if (configured != null) {
            return configured;
        }
        return properties.models().getOrDefault("default", Pricing.zero());
    }
}
