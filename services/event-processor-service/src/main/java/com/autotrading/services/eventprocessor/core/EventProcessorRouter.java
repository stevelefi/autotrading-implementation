package com.autotrading.services.eventprocessor.core;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Routes normalized ingress events to trade events.
 * <p>
 * Deduplication is handled at the consumer layer via {@code ConsumerDeduper}
 * backed by the {@code consumer_inbox} DB table — this router is a pure
 * stateless transform so that restarts do not lose dedup state.
 */
public class EventProcessorRouter {

  public Optional<RoutedTradeEvent> route(NormalizedIngressEvent normalized) {
    if (normalized.agentId() == null || normalized.agentId().isBlank()) {
      return Optional.empty();
    }

    return Optional.of(new RoutedTradeEvent(
        "trade-" + UUID.randomUUID(),
        normalized.ingressEventId(),
        normalized.rawEventId(),
        normalized.traceId(),
        normalized.idempotencyKey(),
        normalized.sourceType(),
        normalized.sourceEventId(),
        normalized.agentId(),
        Instant.now(),
        Map.copyOf(normalized.payload())));
  }
}
