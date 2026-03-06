package com.autotrading.services.eventprocessor.core;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventProcessorRouter {
  private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();

  public Optional<RoutedTradeEvent> route(NormalizedIngressEvent normalized) {
    if (normalized.agentId() == null || normalized.agentId().isBlank()) {
      return Optional.empty();
    }
    String dedupe = normalized.ingressEventId() + ":" + normalized.idempotencyKey();
    if (!seenKeys.add(dedupe)) {
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
