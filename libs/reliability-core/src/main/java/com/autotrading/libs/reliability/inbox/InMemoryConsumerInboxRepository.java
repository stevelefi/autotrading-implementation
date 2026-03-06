package com.autotrading.libs.reliability.inbox;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConsumerInboxRepository implements ConsumerInboxRepository {
  private final Set<String> processed = ConcurrentHashMap.newKeySet();

  @Override
  public boolean tryBegin(String consumerName, String eventId) {
    return processed.add(consumerName + ":" + eventId);
  }
}
