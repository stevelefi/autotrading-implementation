package com.autotrading.libs.reliability.inbox;

public interface ConsumerInboxRepository {
  boolean tryBegin(String consumerName, String eventId);
}
