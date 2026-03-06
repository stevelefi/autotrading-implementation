package com.autotrading.libs.reliability.outbox;

public interface OutboxPublisher {
  void publish(OutboxEvent event) throws Exception;
}
