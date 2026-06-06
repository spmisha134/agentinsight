package com.agentinsight.cost.service;

import com.agentinsight.cost.model.Pricing;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(Map<String, Pricing> models) {
    public PricingProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
    }
}
