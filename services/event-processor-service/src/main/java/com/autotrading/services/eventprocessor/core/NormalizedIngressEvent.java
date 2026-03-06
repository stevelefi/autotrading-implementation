package com.autotrading.services.eventprocessor.core;

import java.time.Instant;
import java.util.Map;

public record NormalizedIngressEvent(
    String ingressEventId,
    String rawEventId,
    String traceId,
    String idempotencyKey,
    String sourceType,
    String sourceEventId,
    String agentId,
    String eventIntent,
    Instant occurredAt,
    Map<String, Object> payload
) {
}
