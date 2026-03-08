package com.autotrading.libs.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxPublisher;

/**
 * {@link OutboxPublisher} implementation backed by {@link DirectKafkaPublisher}.
 *
 * <p>Used by {@link com.autotrading.libs.reliability.outbox.OutboxDispatcher} to dispatch
 * poller-picked outbox events to Kafka. Delegates full retry logic to
 * {@link DirectKafkaPublisher#publish}, which applies doubling-backoff up to 5 s.
 * On exhaustion a {@link KafkaPublishException} is thrown and the dispatcher marks
 * the event as FAILED with a back-off {@code next_retry_at}.
 */
public class KafkaOutboxPublisher implements OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);

  private final DirectKafkaPublisher directKafkaPublisher;

  public KafkaOutboxPublisher(DirectKafkaPublisher directKafkaPublisher) {
    this.directKafkaPublisher = directKafkaPublisher;
  }

  @Override
  public void publish(OutboxEvent event) throws Exception {
    try {
      directKafkaPublisher.publish(event.topic(), event.partitionKey(), event.payload());
      log.debug("outbox published eventId={} topic={}", event.eventId(), event.topic());
    } catch (KafkaPublishException e) {
      log.warn("outbox publish exhausted eventId={} topic={}: {}", event.eventId(), event.topic(), e.getMessage());
      throw e;
    }
  }
}
