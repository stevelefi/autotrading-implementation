package com.autotrading.libs.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotrading.libs.reliability.outbox.InMemoryOutboxRepository;

/**
 * Unit tests for {@link KafkaFirstPublisher}.
 *
 * <p>Verifies Kafka-first publish and outbox fallback paths.
 */
class KafkaFirstPublisherTest {

  /** A TransactionTemplate whose execute() just invokes the callback directly (no real TX). */
  @SuppressWarnings("unchecked")
  private static TransactionTemplate passThroughTxTemplate() {
    TransactionTemplate tpl = mock(TransactionTemplate.class);
    when(tpl.execute(any())).thenAnswer(inv -> {
      TransactionCallback<?> cb = inv.getArgument(0);
      return cb.doInTransaction(null);
    });
    return tpl;
  }

  @Test
  void publishSucceedsOnKafkaHappyPath() {
    DirectKafkaPublisher dkp = mock(DirectKafkaPublisher.class); // void publish → no-op
    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    KafkaFirstPublisher publisher = new KafkaFirstPublisher(dkp, outbox, passThroughTxTemplate());

    publisher.publish("ingress.events.normalized.v1", "agent-1", "{\"msg\":\"ok\"}");

    // Kafka was tried
    verify(dkp).publish("ingress.events.normalized.v1", "agent-1", "{\"msg\":\"ok\"}");
    // Outbox NOT touched on success
    assertThat(outbox.countPending()).isEqualTo(0);
  }

  @Test
  void publishFallsBackToOutboxOnKafkaExhaustion() {
    DirectKafkaPublisher dkp = mock(DirectKafkaPublisher.class);
    doThrow(new KafkaPublishException("ingress.events.normalized.v1", "agent-1",
        new RuntimeException("Redpanda unreachable")))
        .when(dkp).publish(any(), any(), any());

    InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    KafkaFirstPublisher publisher = new KafkaFirstPublisher(dkp, outbox, passThroughTxTemplate());

    publisher.publish("ingress.events.normalized.v1", "agent-1", "{}");

    // One event queued in outbox
    assertThat(outbox.countPending()).isEqualTo(1);
  }

  @Test
  void publishDoesNotPropagateWhenOutboxAlsoFails() {
    DirectKafkaPublisher dkp = mock(DirectKafkaPublisher.class);
    doThrow(new KafkaPublishException("t", "k", new RuntimeException("broker down")))
        .when(dkp).publish(any(), any(), any());

    TransactionTemplate failingTx = mock(TransactionTemplate.class);
    when(failingTx.execute(any())).thenThrow(new RuntimeException("DB is also down"));

    KafkaFirstPublisher publisher = new KafkaFirstPublisher(
        dkp, new InMemoryOutboxRepository(), failingTx);

    // Must not propagate — event may be lost but caller is shielded
    assertThatCode(() -> publisher.publish("t", "k", "p")).doesNotThrowAnyException();
  }
}
