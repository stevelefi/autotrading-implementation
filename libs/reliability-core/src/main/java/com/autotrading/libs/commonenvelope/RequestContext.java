package com.autotrading.libs.commonenvelope;

import java.time.Instant;
import java.util.Objects;

public record RequestContext(
    String traceId,
    String requestId,
    String idempotencyKey,
    String principalId,
    Instant receivedAtUtc
) {
  public RequestContext {
    Objects.requireNonNull(traceId, "traceId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(principalId, "principalId must not be null");
    Objects.requireNonNull(receivedAtUtc, "receivedAtUtc must not be null");
  }
}
