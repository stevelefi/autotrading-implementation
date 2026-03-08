package com.autotrading.libs.reliability.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOutboxRepository implements OutboxRepository {
  private final Map<String, OutboxEvent> events = new ConcurrentHashMap<>();

  @Override
  public void append(OutboxEvent event) {
    events.put(event.eventId(), event);
  }

  /** @deprecated Use {@link #pollRetryable(int)} instead. */
  @Deprecated
  @Override
  public List<OutboxEvent> pollNew(int batchSize) {
    return events.values().stream()
        .filter(e -> e.status() == OutboxStatus.NEW)
        .sorted(Comparator.comparing(OutboxEvent::createdAtUtc))
        .limit(batchSize)
        .toList();
  }

  @Override
  public List<OutboxEvent> pollRetryable(int batchSize) {
    Instant now = Instant.now();
    return events.values().stream()
        .filter(e -> (e.status() == OutboxStatus.NEW || e.status() == OutboxStatus.FAILED)
            && (e.nextRetryAt() == null || !e.nextRetryAt().isAfter(now)))
        .sorted(Comparator.comparing(OutboxEvent::createdAtUtc))
        .limit(batchSize)
        .toList();
  }

  @Override
  public void markDispatched(String eventId) {
    OutboxEvent current = events.get(eventId);
    if (current != null) {
      events.put(eventId, current.markDispatched(Instant.now()));
    }
  }

  @Override
  public void markFailed(String eventId, String error, Instant nextRetryAt) {
    OutboxEvent current = events.get(eventId);
    if (current != null) {
      events.put(eventId, current.markFailed(error, Instant.now(), nextRetryAt));
    }
  }

  @Override
  public long countPending() {
    Instant now = Instant.now();
    return events.values().stream()
        .filter(e -> (e.status() == OutboxStatus.NEW || e.status() == OutboxStatus.FAILED)
            && (e.nextRetryAt() == null || !e.nextRetryAt().isAfter(now)))
        .count();
  }

  public List<OutboxEvent> allEvents() {
    return new ArrayList<>(events.values());
  }
}
