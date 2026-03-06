package com.autotrading.libs.reliability.outbox;

import java.util.function.Supplier;

import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a domain mutation and appends an outbox event within a single database transaction.
 * <p>
 * Both the domain mutation and the outbox append succeed or fail atomically,
 * guaranteeing that every committed domain change has a corresponding outbox event
 * scheduled for relay to Kafka.
 */
public class TransactionalOutboxExecutor {
  private final OutboxRepository outboxRepository;

  public TransactionalOutboxExecutor(OutboxRepository outboxRepository) {
    this.outboxRepository = outboxRepository;
  }

  /**
   * Runs {@code domainMutation} and {@code outboxEventSupplier} inside one transaction.
   * If either fails, both are rolled back.
   */
  @Transactional
  public <T> T execute(Supplier<T> domainMutation, Supplier<OutboxEvent> outboxEventSupplier) {
    T result = domainMutation.get();
    outboxRepository.append(outboxEventSupplier.get());
    return result;
  }
}
