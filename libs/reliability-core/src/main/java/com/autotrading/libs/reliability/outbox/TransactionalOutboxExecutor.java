package com.autotrading.libs.reliability.outbox;

import java.util.function.Supplier;

import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a domain mutation and appends an outbox event within a single database transaction.
 *
 * @deprecated The transactional outbox pattern has been replaced with a Kafka-first approach.
 *     Only {@code ingress-gateway-service} retains the outbox (as a Kafka fallback via
 *     {@link KafkaFirstPublisher}); all other services publish directly to Kafka.
 *     This class will be removed in a future cleanup pass.
 */
@Deprecated
public class TransactionalOutboxExecutor {
  private final OutboxRepository outboxRepository;

  public TransactionalOutboxExecutor(OutboxRepository outboxRepository) {
    this.outboxRepository = outboxRepository;
  }

  /**
   * Runs {@code domainMutation} and {@code outboxEventSupplier} inside one transaction.
   * If either fails, both are rolled back.
   *
   * @deprecated Use direct Kafka publish after the transaction commit instead.
   */
  @Deprecated
  @Transactional
  public <T> T execute(Supplier<T> domainMutation, Supplier<OutboxEvent> outboxEventSupplier) {
    T result = domainMutation.get();
    outboxRepository.append(outboxEventSupplier.get());
    return result;
  }
}

