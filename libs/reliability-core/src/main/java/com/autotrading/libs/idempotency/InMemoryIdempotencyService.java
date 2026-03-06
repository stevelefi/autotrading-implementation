package com.autotrading.libs.idempotency;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyService implements IdempotencyService {
  private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

  @Override
  public synchronized ClaimResult claim(IdempotencyClaim claim) {
    IdempotencyRecord existing = records.get(claim.key());
    if (existing == null) {
      IdempotencyRecord created = new IdempotencyRecord(
          claim.key(),
          claim.payloadHash(),
          IdempotencyStatus.PENDING,
          null,
          null,
          claim.claimedAtUtc());
      records.put(claim.key(), created);
      return new ClaimResult(ClaimOutcome.CLAIMED, created, "claimed");
    }

    if (!existing.payloadHash().equals(claim.payloadHash())) {
      return new ClaimResult(ClaimOutcome.CONFLICT, existing, "same key with different payload");
    }

    return new ClaimResult(ClaimOutcome.REPLAY, existing, "replay existing operation");
  }

  @Override
  public synchronized void markCompleted(String key, String responseSnapshot) {
    IdempotencyRecord current = records.get(key);
    if (current == null) {
      return;
    }
    records.put(key, current.withStatus(IdempotencyStatus.COMPLETED, responseSnapshot, null, Instant.now()));
  }

  @Override
  public synchronized void markFailed(String key, String failureReason) {
    IdempotencyRecord current = records.get(key);
    if (current == null) {
      return;
    }
    records.put(key, current.withStatus(IdempotencyStatus.FAILED, null, failureReason, Instant.now()));
  }

  @Override
  public Optional<IdempotencyRecord> find(String key) {
    return Optional.ofNullable(records.get(key));
  }

  public synchronized void clear() {
    records.clear();
  }
}
