package com.autotrading.libs.reliability.outbox;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls pending outbox events and publishes them to Kafka via {@link OutboxPublisher}.
 * Failed events are marked {@code FAILED} with the error message for later retry or DLQ.
 */
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

  private final OutboxRepository outboxRepository;
  private final OutboxPublisher publisher;
  private final ReliabilityMetrics metrics;

  public OutboxDispatcher(OutboxRepository outboxRepository, OutboxPublisher publisher, ReliabilityMetrics metrics) {
    this.outboxRepository = outboxRepository;
    this.publisher = publisher;
    this.metrics = metrics;
  }

  public int dispatchBatch(int batchSize) {
    List<OutboxEvent> events = outboxRepository.pollNew(batchSize);

    if (events.isEmpty()) {
      metrics.setOutboxBacklogAgeMs(0);
      return 0;
    }

    long maxAge = events.stream()
        .mapToLong(e -> Duration.between(e.createdAtUtc(), Instant.now()).toMillis())
        .max()
        .orElse(0L);
    metrics.setOutboxBacklogAgeMs(maxAge);

    int dispatched = 0;
    for (OutboxEvent event : events) {
      try {
        publisher.publish(event);
        outboxRepository.markDispatched(event.eventId());
        dispatched++;
      } catch (Exception ex) {
        String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        outboxRepository.markFailed(event.eventId(), error);
        log.warn("outbox dispatch failed eventId={} topic={} cause={}", event.eventId(), event.topic(), error, ex);
      }
    }

    if (dispatched > 0) {
      log.debug("outbox dispatched {} of {} events", dispatched, events.size());
    }
    return dispatched;
  }
}
