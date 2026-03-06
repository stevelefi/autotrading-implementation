package com.autotrading.services.monitoring.core;

import com.autotrading.libs.idempotency.ClaimOutcome;
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
  public Map<String, Object> forward(String idempotencyKey, Map<String, Object> payload, String sourceType) {
    String payloadHash = sourceType + ":" + payload.hashCode();
    var claim = idempotency.claim(new IdempotencyClaim(idempotencyKey, payloadHash, Instant.now()));

    if (claim.outcome() == ClaimOutcome.CONFLICT) {
      throw new IllegalArgumentException("idempotency conflict");
    }

    String ingressEventId = ingressEventByKey.computeIfAbsent(idempotencyKey, ignored -> "ing-" + UUID.randomUUID());
    idempotency.markCompleted(idempotencyKey, ingressEventId);

    return Map.of(
        "accepted", true,
        "ingress_event_id", ingressEventId,
        "received_at", Instant.now().toString(),
        "status", claim.outcome() == ClaimOutcome.REPLAY ? "ACCEPTED" : "ACCEPTED");
  }
}
