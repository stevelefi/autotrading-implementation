package com.autotrading.services.risk.db;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface PolicyDecisionLogRepository extends ListCrudRepository<PolicyDecisionLogEntity, String> {
  List<PolicyDecisionLogEntity> findByRiskDecisionId(String riskDecisionId);
  List<PolicyDecisionLogEntity> findByAgentIdOrderByCreatedAtDesc(String agentId);
}
