package com.autotrading.services.agentruntime.db;

import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface SignalRepository extends ListCrudRepository<SignalEntity, String> {
  Optional<SignalEntity> findByClientEventId(String clientEventId);
  boolean existsByClientEventId(String clientEventId);
  boolean existsByTradeEventId(String tradeEventId);
}
