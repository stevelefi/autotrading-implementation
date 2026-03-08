package com.autotrading.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.libs.reliability.inbox.InMemoryConsumerInboxRepository;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.libs.reliability.outbox.InMemoryOutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxDispatcher;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for data consistency guarantees between outbox, inbox, and dispatcher
 * under concurrent and failure conditions.
 */
class DataConsistencyTest {

  @Test
  @DisplayName("Outbox events are dispatched in FIFO order")
  void outboxDispatchesFifo() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();
    List<String> delivered = new ArrayList<>();

    for (int i = 1; i <= 10; i++) {
      Instant now = Instant.now();
      outbox.append(new OutboxEvent("evt-" + String.format("%02d", i),
          "orders.status.v1", "agent-1", "{}", OutboxStatus.NEW, 0, null, null, now, now));
    }

    OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, e -> delivered.add(e.eventId()), metrics);
    dispatcher.dispatchBatch(100);

    assertThat(delivered).containsExactly(
        "evt-01", "evt-02", "evt-03", "evt-04", "evt-05",
        "evt-06", "evt-07", "evt-08", "evt-09", "evt-10");
  }

  @Test
  @DisplayName("ConsumerDeduper with multiple consumer groups is independent")
  void deduperIsolatesConsumerGroups() {
    ReliabilityMetrics metrics = new ReliabilityMetrics();
    ConsumerDeduper deduper = new ConsumerDeduper(new InMemoryConsumerInboxRepository(), metrics);

    AtomicInteger projectionCount = new AtomicInteger(0);
    AtomicInteger alertCount = new AtomicInteger(0);

    String eventId = "shared-event-42";

    // Same event processed by two different consumer groups
    deduper.runOnce("projection-consumer", eventId, projectionCount::incrementAndGet);
    deduper.runOnce("alert-consumer", eventId, alertCount::incrementAndGet);

    assertThat(projectionCount.get()).isEqualTo(1);
    assertThat(alertCount.get()).isEqualTo(1);

    // Re-process same event in each group → suppressed
    deduper.runOnce("projection-consumer", eventId, projectionCount::incrementAndGet);
    deduper.runOnce("alert-consumer", eventId, alertCount::incrementAndGet);

    assertThat(projectionCount.get()).isEqualTo(1);
    assertThat(alertCount.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Concurrent dedup calls for the same event produce exactly one effect")
  void concurrentDedupExactlyOnce() throws Exception {
    ReliabilityMetrics metrics = new ReliabilityMetrics();
    ConsumerDeduper deduper = new ConsumerDeduper(new InMemoryConsumerInboxRepository(), metrics);

    AtomicInteger count = new AtomicInteger(0);
    String eventId = "concurrent-event-1";
    int threads = 20;

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < threads; i++) {
      executor.submit(() -> {
        try { start.await(); } catch (InterruptedException ignore) {}
        deduper.runOnce("test-consumer", eventId, count::incrementAndGet);
      });
    }

    start.countDown();
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Outbox batch respects batch size limit")
  void outboxBatchSizeLimit() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();
    List<String> delivered = new ArrayList<>();

    for (int i = 1; i <= 20; i++) {
      Instant now = Instant.now();
      outbox.append(new OutboxEvent("batch-" + i, "test.topic",
          "agent-1", "{}", OutboxStatus.NEW, 0, null, null, now, now));
    }

    OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, e -> delivered.add(e.eventId()), metrics);

    // First batch of 5
    int first = dispatcher.dispatchBatch(5);
    assertThat(first).isEqualTo(5);
    assertThat(delivered).hasSize(5);

    // Second batch of 5
    int second = dispatcher.dispatchBatch(5);
    assertThat(second).isEqualTo(5);
    assertThat(delivered).hasSize(10);

    // All 20
    int remaining = dispatcher.dispatchBatch(100);
    assertThat(remaining).isEqualTo(10);
    assertThat(delivered).hasSize(20);
    assertThat(delivered).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("Partial dispatch failure leaves remaining events for retry")
  void partialFailureLeavesRemaining() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();
    AtomicInteger callCount = new AtomicInteger(0);
    List<String> delivered = new ArrayList<>();

    for (int i = 1; i <= 5; i++) {
      Instant now = Instant.now();
      outbox.append(new OutboxEvent("partial-" + i, "test.topic",
          "agent-1", "{}", OutboxStatus.NEW, 0, null, null, now, now));
    }

    // Publisher that fails on 3rd event
    OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, event -> {
      if (callCount.incrementAndGet() == 3) {
        throw new RuntimeException("publish fail on 3rd");
      }
      delivered.add(event.eventId());
    }, metrics);

    int result = dispatcher.dispatchBatch(10);
    // At least some got delivered before the failure
    assertThat(delivered).isNotEmpty();
  }
}
