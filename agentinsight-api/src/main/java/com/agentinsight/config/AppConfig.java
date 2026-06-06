package com.agentinsight.config;

import com.agentinsight.cost.service.PricingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AgentInsightProperties.class, PricingProperties.class})
public class AppConfig {}
