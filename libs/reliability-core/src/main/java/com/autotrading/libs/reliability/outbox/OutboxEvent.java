package com.autotrading.libs.reliability.outbox;

import java.time.Instant;
import java.util.Objects;

public record OutboxEvent(
    String eventId,
    String topic,
    String partitionKey,
    String payload,
    OutboxStatus status,
    int attempts,
    String lastError,
    /** When the poller should next attempt this event; {@code null} means eligible immediately. */
    Instant nextRetryAt,
    Instant createdAtUtc,
    Instant updatedAtUtc
) {
  public OutboxEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(topic, "topic");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAtUtc, "createdAtUtc");
    Objects.requireNonNull(updatedAtUtc, "updatedAtUtc");
  }

  public OutboxEvent markDispatched(Instant nowUtc) {
    return new OutboxEvent(eventId, topic, partitionKey, payload, OutboxStatus.DISPATCHED, attempts + 1, null, null, createdAtUtc, nowUtc);
  }

  /**
   * Returns a copy of this event marked as FAILED with exponential-backoff retry scheduling.
   *
   * @param error       the error message to record
   * @param nowUtc      current time
   * @param nextRetryAt when the poller should retry; {@code null} to stop retrying
   */
  public OutboxEvent markFailed(String error, Instant nowUtc, Instant nextRetryAt) {
    return new OutboxEvent(eventId, topic, partitionKey, payload, OutboxStatus.FAILED, attempts + 1, error, nextRetryAt, createdAtUtc, nowUtc);
  }
}
