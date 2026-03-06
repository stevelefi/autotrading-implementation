package com.autotrading.services.ibkr.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, String> {
  List<ExecutionEntity> findByOrderIntentIdOrderByFillTsAsc(String orderIntentId);
  List<ExecutionEntity> findByAgentIdOrderByFillTsDesc(String agentId);
}
