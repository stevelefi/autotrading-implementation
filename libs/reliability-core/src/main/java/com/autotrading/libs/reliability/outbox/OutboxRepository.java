package com.autotrading.libs.reliability.outbox;

import java.util.List;

public interface OutboxRepository {
  void append(OutboxEvent event);

  List<OutboxEvent> pollNew(int batchSize);

  void markDispatched(String eventId);

  void markFailed(String eventId, String error);

  long countPending();
}
