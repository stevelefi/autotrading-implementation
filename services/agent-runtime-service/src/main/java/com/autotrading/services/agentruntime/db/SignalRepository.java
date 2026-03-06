package com.autotrading.services.agentruntime.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalRepository extends JpaRepository<SignalEntity, String> {
  Optional<SignalEntity> findByIdempotencyKey(String idempotencyKey);
  boolean existsByIdempotencyKey(String idempotencyKey);
  boolean existsByTradeEventId(String tradeEventId);
}
