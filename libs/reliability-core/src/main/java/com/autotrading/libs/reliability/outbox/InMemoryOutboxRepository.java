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

  @Override
  public List<OutboxEvent> pollNew(int batchSize) {
    return events.values().stream()
        .filter(e -> e.status() == OutboxStatus.NEW)
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
  public void markFailed(String eventId, String error) {
    OutboxEvent current = events.get(eventId);
    if (current != null) {
      events.put(eventId, current.markFailed(error, Instant.now()));
    }
  }

  @Override
  public long countPending() {
    return events.values().stream().filter(e -> e.status() == OutboxStatus.NEW).count();
  }

  public List<OutboxEvent> allEvents() {
    return new ArrayList<>(events.values());
  }
}
