package com.autotrading.libs.reliability.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;

/**
 * Polls retryable outbox events (status NEW or FAILED with past next_retry_at) and
 * publishes them to Kafka via {@link OutboxPublisher}.
 * <p>
 * On publish failure, events are re-scheduled with exponential back-off capped at
 * {@value #MAX_BACKOFF_SECONDS} seconds. After {@value #MAX_ATTEMPTS} attempts the
 * next_retry_at is set to {@code null}, permanently parking the event as FAILED so
 * an alert can be raised.
 */
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

  /** Maximum back-off between retries (5 minutes). */
  static final long MAX_BACKOFF_SECONDS = 300L;
  /** After this many attempts the event is permanently parked as FAILED (no next_retry_at). */
  static final int MAX_ATTEMPTS = 10;

  private final OutboxRepository outboxRepository;
  private final OutboxPublisher publisher;
  private final ReliabilityMetrics metrics;

  public OutboxDispatcher(OutboxRepository outboxRepository, OutboxPublisher publisher, ReliabilityMetrics metrics) {
    this.outboxRepository = outboxRepository;
    this.publisher = publisher;
    this.metrics = metrics;
  }

  public int dispatchBatch(int batchSize) {
    List<OutboxEvent> events = outboxRepository.pollRetryable(batchSize);

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
        int nextAttempt = event.attempts() + 1;
        Instant nextRetryAt = computeNextRetryAt(nextAttempt);
        outboxRepository.markFailed(event.eventId(), error, nextRetryAt);
        if (nextRetryAt != null) {
          log.warn("outbox dispatch failed eventId={} topic={} attempt={} nextRetry={} cause={}",
              event.eventId(), event.topic(), nextAttempt, nextRetryAt, error);
        } else {
          log.error("outbox event permanently parked eventId={} topic={} attempts={} cause={}",
              event.eventId(), event.topic(), nextAttempt, error);
        }
      }
    }

    if (dispatched > 0) {
      log.debug("outbox dispatched {} of {} events", dispatched, events.size());
    }
    return dispatched;
  }

  /**
   * Returns the next retry timestamp using capped exponential back-off, or {@code null}
   * if {@code attempt} has reached {@value #MAX_ATTEMPTS} (permanently park).
   */
  static Instant computeNextRetryAt(int attempt) {
    if (attempt >= MAX_ATTEMPTS) {
      return null;
    }
    long backoffSeconds = Math.min((long) Math.pow(2, attempt), MAX_BACKOFF_SECONDS);
    return Instant.now().plusSeconds(backoffSeconds);
  }
}
