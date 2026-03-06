package com.autotrading.services.monitoring.core;

import java.time.Instant;

public record SystemControlState(
    boolean killSwitch,
    MonitoringTradingMode tradingMode,
    Instant updatedAtUtc,
    String actorId,
    String requestId
) {
  public static SystemControlState initial() {
    return new SystemControlState(false, MonitoringTradingMode.NORMAL, Instant.now(), "system", "bootstrap");
  }
}
