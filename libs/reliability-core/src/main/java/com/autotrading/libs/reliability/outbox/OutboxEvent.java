package com.autotrading.libs.reliability.outbox;

import java.time.Instant;

public record OutboxEvent(
    String eventId,
    String topic,
    String partitionKey,
    String payload,
    OutboxStatus status,
    int attempts,
    String lastError,
    Instant createdAtUtc,
    Instant updatedAtUtc
) {
  public OutboxEvent markDispatched(Instant nowUtc) {
    return new OutboxEvent(eventId, topic, partitionKey, payload, OutboxStatus.DISPATCHED, attempts + 1, null, createdAtUtc, nowUtc);
  }

  public OutboxEvent markFailed(String error, Instant nowUtc) {
    return new OutboxEvent(eventId, topic, partitionKey, payload, OutboxStatus.FAILED, attempts + 1, error, createdAtUtc, nowUtc);
  }
}
