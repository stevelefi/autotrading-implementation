package com.autotrading.libs.idempotency;

public enum ClaimOutcome {
  /** This is the first time this key has been seen — proceed with execution. */
  CLAIMED,
  /** Key already exists (same or different payload) — first-write-wins: return original response. */
  REPLAY
}
