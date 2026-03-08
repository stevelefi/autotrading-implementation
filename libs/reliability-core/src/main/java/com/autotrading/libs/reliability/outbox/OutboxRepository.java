package com.autotrading.libs.reliability.outbox;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository {
  void append(OutboxEvent event);

  /** @deprecated Use {@link #pollRetryable(int)} instead — also covers FAILED events. */
  @Deprecated
  List<OutboxEvent> pollNew(int batchSize);

  /**
   * Returns up to {@code batchSize} events whose status is {@code NEW} or {@code FAILED}
   * and whose {@code next_retry_at} is in the past (or NULL).
   */
  List<OutboxEvent> pollRetryable(int batchSize);

  void markDispatched(String eventId);

  /**
   * Marks the event as FAILED and schedules the next retry.
   *
   * @param nextRetryAt when the poller should try again; {@code null} means no further retries
   */
  void markFailed(String eventId, String error, Instant nextRetryAt);

  long countPending();
}
