package com.autotrading.services.agentruntime.db;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface SignalRepository extends ListCrudRepository<SignalEntity, String> {
  Optional<SignalEntity> findByIdempotencyKey(String idempotencyKey);
  boolean existsByIdempotencyKey(String idempotencyKey);
  boolean existsByTradeEventId(String tradeEventId);
}
