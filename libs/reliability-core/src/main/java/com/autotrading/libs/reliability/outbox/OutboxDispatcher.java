package com.autotrading.libs.reliability.outbox;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class OutboxDispatcher {
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
    if (!events.isEmpty()) {
      long maxAge = events.stream()
          .mapToLong(e -> Duration.between(e.createdAtUtc(), Instant.now()).toMillis())
          .max()
          .orElse(0L);
      metrics.setOutboxBacklogAgeMs(maxAge);
    }

    int dispatched = 0;
    for (OutboxEvent event : events) {
      try {
        publisher.publish(event);
        outboxRepository.markDispatched(event.eventId());
        dispatched++;
      } catch (Exception ex) {
        outboxRepository.markFailed(event.eventId(), ex.getMessage());
      }
    }
    return dispatched;
  }
}
