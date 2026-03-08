package com.autotrading.libs.reliability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.libs.reliability.inbox.InMemoryConsumerInboxRepository;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.libs.reliability.outbox.InMemoryOutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxDispatcher;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import com.autotrading.libs.reliability.outbox.TransactionalOutboxExecutor;

class OutboxInboxReliabilityTest {

  @Test
  void outboxAppendsOnlyAfterMutationSuccess() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    TransactionalOutboxExecutor executor = new TransactionalOutboxExecutor(outbox);

    String result = executor.execute(
        () -> "ok",
        () -> new OutboxEvent(UUID.randomUUID().toString(), "topic", "k", "payload", OutboxStatus.NEW, 0, null, null, Instant.now(), Instant.now()));

    assertThat(result).isEqualTo("ok");
    assertThat(outbox.countPending()).isEqualTo(1);

    assertThatThrownBy(() -> executor.execute(
        () -> {
          throw new IllegalStateException("mutation failed");
        },
        () -> new OutboxEvent(UUID.randomUUID().toString(), "topic", "k", "payload", OutboxStatus.NEW, 0, null, null, Instant.now(), Instant.now())))
        .isInstanceOf(IllegalStateException.class);

    assertThat(outbox.countPending()).isEqualTo(1);
  }

  @Test
  void dispatcherAndInboxDedupeHandleReplaySafely() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();
    List<String> published = new ArrayList<>();

    OutboxEvent event = new OutboxEvent("evt-1", "topic", "k", "payload", OutboxStatus.NEW, 0, null, null, Instant.now(), Instant.now());
    outbox.append(event);

    OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, e -> published.add(e.eventId()), metrics);
    int dispatched = dispatcher.dispatchBatch(10);

    assertThat(dispatched).isEqualTo(1);
    assertThat(published).containsExactly("evt-1");

    ConsumerDeduper deduper = new ConsumerDeduper(new InMemoryConsumerInboxRepository(), metrics);
    List<String> sideEffects = new ArrayList<>();

    boolean first = deduper.runOnce("consumer-a", "evt-1", () -> sideEffects.add("applied"));
    boolean second = deduper.runOnce("consumer-a", "evt-1", () -> sideEffects.add("applied-again"));

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(sideEffects).containsExactly("applied");
    assertThat(metrics.duplicateSuppressionCount()).isEqualTo(1);
  }
}
