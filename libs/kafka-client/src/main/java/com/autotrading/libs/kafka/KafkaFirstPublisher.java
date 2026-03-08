package com.autotrading.libs.kafka;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxStatus;

/**
 * Kafka-first publisher with transactional outbox fallback.
 *
 * <p>Used exclusively by {@code ingress-gateway-service}. Delegates the synchronous Kafka
 * publish to {@link DirectKafkaPublisher} (which handles doubling-backoff retry up to 5 s).
 * If all retries are exhausted, falls back to writing the event to the
 * {@code outbox_events} table in a new transaction so the {@link com.autotrading.libs.reliability.outbox.OutboxPollerLifecycle}
 * can retry it with exponential back-off from there.
 *
 * <p>This bean must be called from <em>outside</em> a database transaction (e.g. from a
 * {@code TransactionSynchronization.afterCommit()} callback) so that the fallback outbox
 * write starts its own independent transaction.
 */
public class KafkaFirstPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaFirstPublisher.class);

  private final DirectKafkaPublisher directKafkaPublisher;
  private final OutboxRepository outboxRepository;
  private final TransactionTemplate requiresNewTx;

  public KafkaFirstPublisher(DirectKafkaPublisher directKafkaPublisher,
                              OutboxRepository outboxRepository,
                              TransactionTemplate requiresNewTx) {
    this.directKafkaPublisher = directKafkaPublisher;
    this.outboxRepository = outboxRepository;
    this.requiresNewTx = requiresNewTx;
  }

  /**
   * Tries to publish {@code payload} to {@code topic} immediately via
   * {@link DirectKafkaPublisher} (with built-in doubling-backoff retry up to 5 s).
   * On exhaustion, falls back to appending the event to the outbox.
   *
   * @param topic        the Kafka topic
   * @param partitionKey Kafka message key (used for partition routing)
   * @param payload      the serialised JSON payload
   */
  public void publish(String topic, String partitionKey, String payload) {
    try {
      directKafkaPublisher.publish(topic, partitionKey, payload);
      log.debug("kafka-first published ok topic={} partitionKey={}", topic, partitionKey);
    } catch (KafkaPublishException e) {
      log.warn("kafka-first publish exhausted topic={} – falling back to outbox: {}",
          topic, e.getMessage());
      writeToOutbox(topic, partitionKey, payload);
    }
  }

  private void writeToOutbox(String topic, String partitionKey, String payload) {
    try {
      Instant now = Instant.now();
      OutboxEvent fallback = new OutboxEvent(
          UUID.randomUUID().toString(),
          topic,
          partitionKey,
          payload,
          OutboxStatus.NEW,
          0,
          null,   // lastError — event hasn't been tried by outbox poller yet
          null,   // nextRetryAt — eligible immediately
          now,
          now);
      requiresNewTx.execute(status -> {
        outboxRepository.append(fallback);
        return null;
      });
      log.info("kafka-first fallback: event queued in outbox topic={} eventId={}",
          topic, fallback.eventId());
    } catch (Exception ex) {
      log.error("kafka-first outbox fallback FAILED topic={} cause={} – event may be lost",
          topic, ex.getMessage(), ex);
    }
  }
}
