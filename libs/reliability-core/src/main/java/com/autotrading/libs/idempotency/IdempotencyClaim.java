package com.autotrading.libs.idempotency;

import java.time.Instant;
import java.util.Objects;

public record IdempotencyClaim(String key, String payloadHash, Instant claimedAtUtc) {
  public IdempotencyClaim {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(payloadHash, "payloadHash must not be null");
    Objects.requireNonNull(claimedAtUtc, "claimedAtUtc must not be null");
  }
}
