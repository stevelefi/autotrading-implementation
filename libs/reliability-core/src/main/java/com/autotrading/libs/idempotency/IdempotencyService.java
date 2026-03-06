package com.autotrading.libs.idempotency;

import java.util.Optional;

public interface IdempotencyService {
  ClaimResult claim(IdempotencyClaim claim);

  void markCompleted(String key, String responseSnapshot);

  void markFailed(String key, String failureReason);

  Optional<IdempotencyRecord> find(String key);
}
