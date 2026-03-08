package com.autotrading.services.ibkr.db;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface ExecutionRepository extends ListCrudRepository<ExecutionEntity, String> {
  List<ExecutionEntity> findByOrderIntentIdOrderByFillTsAsc(String orderIntentId);
  List<ExecutionEntity> findByAgentIdOrderByFillTsDesc(String agentId);
}
