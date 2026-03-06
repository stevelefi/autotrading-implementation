package com.autotrading.libs.reliability.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class ReliabilityMetrics {
  private final AtomicLong duplicateSuppressionCount = new AtomicLong();
  private final AtomicLong firstStatusTimeoutCount = new AtomicLong();
  private final AtomicLong outboxBacklogAgeMs = new AtomicLong();

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
