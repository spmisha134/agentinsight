package com.agentinsight.health.model;

import java.util.Map;

public record HealthResponse(String status, Map<String, String> checks) {}
