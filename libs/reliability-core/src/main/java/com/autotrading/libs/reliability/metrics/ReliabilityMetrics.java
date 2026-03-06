package com.autotrading.libs.reliability.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;

public class ReliabilityMetrics {
  private final AtomicLong duplicateSuppressionCount = new AtomicLong();
  private final AtomicLong firstStatusTimeoutCount = new AtomicLong();
  private final AtomicLong outboxBacklogAgeMs = new AtomicLong();
  private final MeterRegistry meterRegistry;

  public ReliabilityMetrics() {
    this(null);
  }

  public ReliabilityMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    registerMeters();
  }

  private void registerMeters() {
    if (meterRegistry == null) {
      return;
    }
    Gauge.builder("autotrading.reliability.duplicate.suppression.count", duplicateSuppressionCount, AtomicLong::doubleValue)
        .description("Number of duplicate events suppressed by consumer inbox dedupe")
        .register(meterRegistry);
    Gauge.builder("autotrading.reliability.first.status.timeout.count", firstStatusTimeoutCount, AtomicLong::doubleValue)
        .description("Number of 60-second first-status timeout breaches")
        .register(meterRegistry);
    Gauge.builder("autotrading.reliability.outbox.backlog.age.ms", outboxBacklogAgeMs, AtomicLong::doubleValue)
        .description("Age in milliseconds of oldest pending outbox event")
        .register(meterRegistry);
  }

  public long incrementDuplicateSuppressionCount() {
    return duplicateSuppressionCount.incrementAndGet();
  }

  public long incrementFirstStatusTimeoutCount() {
    return firstStatusTimeoutCount.incrementAndGet();
  }

  public long duplicateSuppressionCount() {
    return duplicateSuppressionCount.get();
  }

  public long firstStatusTimeoutCount() {
    return firstStatusTimeoutCount.get();
  }

  public long outboxBacklogAgeMs() {
    return outboxBacklogAgeMs.get();
  }

  public void setOutboxBacklogAgeMs(long ageMs) {
    outboxBacklogAgeMs.set(Math.max(ageMs, 0));
  }

  public void reset() {
    duplicateSuppressionCount.set(0);
    firstStatusTimeoutCount.set(0);
    outboxBacklogAgeMs.set(0);
  }
}
