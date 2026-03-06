package com.autotrading.libs.idempotency;

public record ClaimResult(
    ClaimOutcome outcome,
    IdempotencyRecord record,
    String message
) {
}
