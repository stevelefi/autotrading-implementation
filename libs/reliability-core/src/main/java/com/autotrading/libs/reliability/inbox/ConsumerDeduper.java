package com.autotrading.libs.reliability.inbox;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates consumer-side exactly-once processing using the inbox table.
 * <p>
 * On first encounter of {@code (consumerName, eventId)} the inbox row is inserted
 * and the {@code action} is executed. On duplicate encounters the action is skipped
 * and the duplicate-suppression counter is incremented.
 * <p>
 * If the action throws, the exception propagates so the Kafka offset is <b>not</b>
 * committed and the message can be retried. The inbox row remains; on retry the
 * action will <b>not</b> re-execute (dedupe still applies). Callers that need
 * at-least-once action execution should ensure the action itself is idempotent or
 * use a compensating mechanism.
 */
public class ConsumerDeduper {

  private static final Logger log = LoggerFactory.getLogger(ConsumerDeduper.class);

  private final ConsumerInboxRepository inboxRepository;
  private final ReliabilityMetrics metrics;

  public ConsumerDeduper(ConsumerInboxRepository inboxRepository, ReliabilityMetrics metrics) {
    this.inboxRepository = inboxRepository;
    this.metrics = metrics;
  }

  /**
   * @return {@code true} if the action was executed; {@code false} if the event was a duplicate.
   * @throws RuntimeException propagated from {@code action} if it fails.
   */
  public boolean runOnce(String consumerName, String eventId, Runnable action) {
    if (!inboxRepository.tryBegin(consumerName, eventId)) {
      metrics.incrementDuplicateSuppressionCount();
      log.debug("consumer dedup suppressed consumer={} eventId={}", consumerName, eventId);
      return false;
    }
    try {
      action.run();
    } catch (RuntimeException ex) {
      log.warn("consumer action failed consumer={} eventId={} cause={}", consumerName, eventId, ex.getMessage());
      throw ex;
    }
    return true;
  }
}
