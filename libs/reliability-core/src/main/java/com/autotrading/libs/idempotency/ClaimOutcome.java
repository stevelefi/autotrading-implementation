package com.autotrading.libs.idempotency;

public enum ClaimOutcome {
  CLAIMED,
  REPLAY,
  CONFLICT
}
