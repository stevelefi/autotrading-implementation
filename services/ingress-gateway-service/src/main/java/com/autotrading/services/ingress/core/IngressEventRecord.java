package com.autotrading.services.ingress.core;

import java.time.Instant;

public record IngressEventRecord(
    String clientEventId,
    String payloadHash,
    String eventId,
    Instant receivedAt,
    String status
) {
}
