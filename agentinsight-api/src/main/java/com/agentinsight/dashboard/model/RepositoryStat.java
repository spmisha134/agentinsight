package com.agentinsight.dashboard.model;

import java.math.BigDecimal;

public record RepositoryStat(String repository, long sessions, long tokens, BigDecimal cost) {}
