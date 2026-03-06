package com.autotrading.libs.reliability.outbox;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Kafka-backed {@link OutboxPublisher}.
 * <p>
 * Sends the event payload to the configured Kafka topic using the event's
 * {@code partitionKey} as the Kafka message key. The send is done synchronously
 * (with a 5-second timeout) to ensure at-least-once delivery before the outbox
 * row is marked dispatched by the caller ({@link OutboxDispatcher}).
 */
public class KafkaOutboxPublisher implements OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
  private static final long SEND_TIMEOUT_SECONDS = 5;

  private final KafkaTemplate<String, String> kafkaTemplate;

  public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void publish(OutboxEvent event) throws Exception {
    try {
      SendResult<String, String> result = kafkaTemplate
          .send(event.topic(), event.partitionKey(), event.payload())
          .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.debug("outbox published eventId={} topic={} partition={} offset={}",
          event.eventId(),
          event.topic(),
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset());
    } catch (ExecutionException | TimeoutException e) {
      log.warn("outbox publish failed eventId={} topic={}: {}", event.eventId(), event.topic(), e.getMessage());
      throw e;
    }
  }
}
