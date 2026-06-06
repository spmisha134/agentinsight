package com.agentinsight.dashboard.model;

import java.math.BigDecimal;

public record ModelStat(String model, long sessions, long tokens, BigDecimal cost) {}
