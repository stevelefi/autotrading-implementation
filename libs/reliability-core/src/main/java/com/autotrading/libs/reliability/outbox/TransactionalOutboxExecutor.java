package com.autotrading.libs.reliability.outbox;

import java.util.function.Supplier;

public class TransactionalOutboxExecutor {
  private final OutboxRepository outboxRepository;

  public TransactionalOutboxExecutor(OutboxRepository outboxRepository) {
    this.outboxRepository = outboxRepository;
  }

  public <T> T execute(Supplier<T> domainMutation, Supplier<OutboxEvent> outboxEventSupplier) {
    T result = domainMutation.get();
    outboxRepository.append(outboxEventSupplier.get());
    return result;
  }
}
