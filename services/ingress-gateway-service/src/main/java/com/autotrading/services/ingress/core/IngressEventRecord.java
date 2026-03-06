package com.autotrading.services.ingress.core;

import java.time.Instant;

public record IngressEventRecord(
    String idempotencyKey,
    String payloadHash,
    String ingressEventId,
    String traceId,
    Instant receivedAt,
    String status
) {
}
