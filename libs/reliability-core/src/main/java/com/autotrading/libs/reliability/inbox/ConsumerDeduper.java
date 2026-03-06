package com.autotrading.libs.reliability.inbox;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;

public class ConsumerDeduper {
  private final ConsumerInboxRepository inboxRepository;
  private final ReliabilityMetrics metrics;

  public ConsumerDeduper(ConsumerInboxRepository inboxRepository, ReliabilityMetrics metrics) {
    this.inboxRepository = inboxRepository;
    this.metrics = metrics;
  }

  public boolean runOnce(String consumerName, String eventId, Runnable action) {
    if (!inboxRepository.tryBegin(consumerName, eventId)) {
      metrics.incrementDuplicateSuppressionCount();
      return false;
    }
    action.run();
    return true;
  }
}
