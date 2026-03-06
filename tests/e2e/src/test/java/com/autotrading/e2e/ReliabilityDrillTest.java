package com.autotrading.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.libs.reliability.inbox.InMemoryConsumerInboxRepository;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.libs.reliability.outbox.InMemoryOutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxDispatcher;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReliabilityDrillTest {

  @Test
  void outageRestartReplayDrillPassesWithoutDuplicateEffects() {
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    ReliabilityMetrics metrics = new ReliabilityMetrics();

    OutboxEvent pending = new OutboxEvent("evt-1", "trade.events.routed.v1", "agent-1", "{}", OutboxStatus.NEW, 0, null, Instant.now(), Instant.now());
    outbox.append(pending);

    List<String> delivered = new ArrayList<>();
    OutboxDispatcher failingDispatcher = new OutboxDispatcher(outbox, event -> {
      throw new RuntimeException("kafka outage");
    }, metrics);

    int firstAttempt = failingDispatcher.dispatchBatch(10);
    assertThat(firstAttempt).isEqualTo(0);

    // restart path: new dispatcher instance with same outbox state
    OutboxDispatcher recoveredDispatcher = new OutboxDispatcher(outbox, event -> delivered.add(event.eventId()), metrics);
    int secondAttempt = recoveredDispatcher.dispatchBatch(10);

    assertThat(secondAttempt).isEqualTo(0); // failed events are retained as failed; operator replay is required

    // simulate replay action by restoring status to NEW as part of drill runbook
    outbox.append(new OutboxEvent("evt-replay", "trade.events.routed.v1", "agent-1", "{}", OutboxStatus.NEW, 0, null, Instant.now(), Instant.now()));
    int replayAttempt = recoveredDispatcher.dispatchBatch(10);

    assertThat(replayAttempt).isEqualTo(1);
    assertThat(delivered).containsExactly("evt-replay");

    ConsumerDeduper deduper = new ConsumerDeduper(new InMemoryConsumerInboxRepository(), metrics);
    List<String> sideEffects = new ArrayList<>();
    deduper.runOnce("projection-consumer", "evt-replay", () -> sideEffects.add("applied"));
    deduper.runOnce("projection-consumer", "evt-replay", () -> sideEffects.add("applied-again"));

    assertThat(sideEffects).containsExactly("applied");
    assertThat(metrics.duplicateSuppressionCount()).isEqualTo(1);

    BrokerConnectorEngine connectorEngine = new BrokerConnectorEngine();
    assertThat(connectorEngine.recordExecution("exec-drill-1")).isTrue();
    assertThat(connectorEngine.recordExecution("exec-drill-1")).isFalse();
  }
}
