package com.autotrading.libs.commonenvelope;

import java.time.Instant;
import java.util.Objects;

public record EventEnvelope<T>(
    String eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    RequestContext context,
    String agentId,
    String instrumentId,
    T payload
) {
  public EventEnvelope {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
  }
}
