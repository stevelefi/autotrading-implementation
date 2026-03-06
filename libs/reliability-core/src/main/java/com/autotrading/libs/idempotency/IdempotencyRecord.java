package com.autotrading.libs.idempotency;

import java.time.Instant;

public record IdempotencyRecord(
    String key,
    String payloadHash,
    IdempotencyStatus status,
    String responseSnapshot,
    String failureReason,
    Instant updatedAtUtc
) {
  public IdempotencyRecord withStatus(IdempotencyStatus nextStatus, String response, String failure, Instant nowUtc) {
    return new IdempotencyRecord(key, payloadHash, nextStatus, response, failure, nowUtc);
  }
}
