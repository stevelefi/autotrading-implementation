package com.autotrading.libs.reliability.outbox;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;

/**
 * Unit tests for {@link OutboxDispatcher}'s exponential back-off behaviour.
 *
 * <p>Sits in the same package to access the package-private
 * {@link OutboxDispatcher#computeNextRetryAt(int)} method.
 */
class OutboxDispatcherBackoffTest {

  // ── computeNextRetryAt ──────────────────────────────────────────────────────

  @Test
  void firstRetryBackoffIsOneSecond() {
    // attempt=1 → 2^1 = 2 seconds
    Instant before = Instant.now();
    Instant nextRetry = OutboxDispatcher.computeNextRetryAt(1);
    Instant after = Instant.now();

    assertThat(nextRetry).isBetween(before.plusSeconds(1), after.plusSeconds(3));
  }

  @Test
  void backoffDoublesWithEachAttempt() {
    Instant before = Instant.now();
    Instant retry3 = OutboxDispatcher.computeNextRetryAt(3); // 2^3 = 8 s
    Instant retry4 = OutboxDispatcher.computeNextRetryAt(4); // 2^4 = 16 s

    assertThat(retry4).isAfter(retry3);
    // retry4 should be ~8 s later than retry3
    long gap = retry4.getEpochSecond() - retry3.getEpochSecond();
    assertThat(gap).isBetween(6L, 10L);
  }

  @Test
  void backoffCapsAtMaxBackoffSeconds() {
    // attempt=9: 2^9=512 > MAX_BACKOFF_SECONDS(300) → should cap at 300
    Instant before = Instant.now();
    Instant nextRetry = OutboxDispatcher.computeNextRetryAt(9);
    Instant after = Instant.now();

    // Must not exceed cap + small clock jitter
    assertThat(nextRetry).isBetween(before.plusSeconds(299), after.plusSeconds(302));
  }

  @Test
  void computeNextRetryAtReturnsNullAtMaxAttempts() {
    // MAX_ATTEMPTS = 10; attempt >= 10 → permanently parked
    assertThat(OutboxDispatcher.computeNextRetryAt(10)).isNull();
    assertThat(OutboxDispatcher.computeNextRetryAt(15)).isNull();
  }

  // ── dispatchBatch integration ───────────────────────────────────────────────

  @Test
  void failedEventUsesBackoffOnNextPoll() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();

    outbox.append(new OutboxEvent(
        "evt-backoff", "trade.events.routed.v1", "agent-1", "{}",
        OutboxStatus.NEW, 0, null, null, Instant.now(), Instant.now()));

    // First dispatch fails — publisher throws
    OutboxDispatcher failDispatcher = new OutboxDispatcher(outbox, event -> {
      throw new RuntimeException("broker down");
    }, metrics);
    int dispatched = failDispatcher.dispatchBatch(10);

    // Dispatch count is 0 (failure case)
    assertThat(dispatched).isEqualTo(0);
  }

  @Test
  void eventPermanentlyParkedAfterMaxAttempts() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();

    // Seed an event that has already reached MAX_ATTEMPTS - 1
    outbox.append(new OutboxEvent(
        "evt-max", "trade.events.routed.v1", "agent-1", "{}",
        OutboxStatus.NEW, OutboxDispatcher.MAX_ATTEMPTS - 1,
        "repeated broker failure", null, Instant.now(), Instant.now()));

    OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, event -> {
      throw new RuntimeException("still down");
    }, metrics);
    int dispatched = dispatcher.dispatchBatch(10);

    // Still 0 dispatched
    assertThat(dispatched).isEqualTo(0);
    // computeNextRetryAt returns null at MAX_ATTEMPTS → event parked permanently
    assertThat(OutboxDispatcher.computeNextRetryAt(OutboxDispatcher.MAX_ATTEMPTS)).isNull();
  }
}
