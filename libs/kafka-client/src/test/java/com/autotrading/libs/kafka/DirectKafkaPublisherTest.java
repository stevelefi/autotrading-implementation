package com.autotrading.libs.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DirectKafkaPublisher}.
 *
 * <p>Uses the package-private 3-arg constructor to inject mock producers and
 * override the total publish budget for fast exhaustion tests.
 */
class DirectKafkaPublisherTest {

  // ── helpers ────────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static Producer<String, String> successProducer() throws Exception {
    Producer<String, String> producer = mock(Producer.class);
    Future<RecordMetadata> future = mock(Future.class);
    when(future.get(anyLong(), any())).thenReturn(null);
    when(producer.send(any())).thenReturn(future);
    return producer;
  }

  @SuppressWarnings("unchecked")
  private static Producer<String, String> failingProducer() throws Exception {
    Producer<String, String> producer = mock(Producer.class);
    Future<RecordMetadata> future = mock(Future.class);
    when(future.get(anyLong(), any()))
        .thenThrow(new ExecutionException(new RuntimeException("broker down")));
    when(producer.send(any())).thenReturn(future);
    return producer;
  }

  // ── publish() ──────────────────────────────────────────────────────────────

  @Test
  void publishSucceedsImmediatelyCallsSendOnce() throws Exception {
    Producer<String, String> producer = successProducer();
    DirectKafkaPublisher publisher = new DirectKafkaPublisher(producer, 500);

    publisher.publish("orders.status.v1", "agent-1", "{\"status\":\"SUBMITTED\"}");

    verify(producer, times(1)).send(any());
  }

  @Test
  void publishThrowsKafkaPublishExceptionAfterBudgetExhausted() throws Exception {
    // Use a 1 ms total budget so the retry loop exits almost immediately
    DirectKafkaPublisher publisher =
        new DirectKafkaPublisher(failingProducer(), 1, 1L);

    assertThatThrownBy(() -> publisher.publish("orders.status.v1", "key", "payload"))
        .isInstanceOf(KafkaPublishException.class)
        .hasMessageContaining("orders.status.v1");
  }

  @Test
  void publishRetriesAfterTransientFailureThenSucceeds() throws Exception {
    Producer<String, String> producer = mock(Producer.class);

    @SuppressWarnings("unchecked")
    Future<RecordMetadata> fail = mock(Future.class);
    when(fail.get(anyLong(), any()))
        .thenThrow(new ExecutionException(new RuntimeException("transient")));

    @SuppressWarnings("unchecked")
    Future<RecordMetadata> ok = mock(Future.class);
    when(ok.get(anyLong(), any())).thenReturn(null);

    // fail once then succeed
    when(producer.send(any()))
        .thenReturn(fail)
        .thenReturn(ok);

    // give enough budget for at least 2 attempts (first sleep = 10 ms)
    DirectKafkaPublisher publisher = new DirectKafkaPublisher(producer, 500, 5_000L);
    publisher.publish("topic", "key", "payload");

    verify(producer, times(2)).send(any());
  }

  // ── publishBestEffort() ────────────────────────────────────────────────────

  @Test
  void publishBestEffortDoesNotThrowOnBudgetExhaustion() throws Exception {
    DirectKafkaPublisher publisher =
        new DirectKafkaPublisher(failingProducer(), 1, 1L);

    assertThatCode(() -> publisher.publishBestEffort("t", "k", "{\"x\":1}"))
        .doesNotThrowAnyException();
  }

  @Test
  void publishBestEffortSucceedsWithoutException() throws Exception {
    DirectKafkaPublisher publisher = new DirectKafkaPublisher(successProducer(), 500);

    assertThatCode(() -> publisher.publishBestEffort("fills.executed.v1", "agent-1", "{}"))
        .doesNotThrowAnyException();
  }
}
