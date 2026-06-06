package com.agentinsight.config;

import com.agentinsight.health.service.HealthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupValidation implements ApplicationRunner {
    private final HealthService healthService;

    public StartupValidation(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public void run(ApplicationArguments args) {
        healthService.validateStartup();
    }
}
