package com.autotrading.services.monitoring.core;

import com.autotrading.libs.idempotency.IdempotencyClaim;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIngressForwarder implements IngressForwarder {
  private final InMemoryIdempotencyService idempotency = new InMemoryIdempotencyService();
  private final Map<String, String> ingressEventByKey = new ConcurrentHashMap<>();

  @Override
  public Map<String, Object> forward(String clientEventId, Map<String, Object> payload, String sourceType) {
    String payloadHash = sourceType + ":" + payload.hashCode();
    var claim = idempotency.claim(new IdempotencyClaim(clientEventId, payloadHash, Instant.now()));

    String eventId = ingressEventByKey.computeIfAbsent(clientEventId, ignored -> UUID.randomUUID().toString());
    idempotency.markCompleted(clientEventId, eventId);

    return Map.of(
        "accepted", true,
        "event_id", eventId,
        "received_at", Instant.now().toString(),
        "status", "ACCEPTED");
  }
}
