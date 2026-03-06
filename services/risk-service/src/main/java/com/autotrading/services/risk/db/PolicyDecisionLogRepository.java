package com.autotrading.services.risk.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyDecisionLogRepository extends JpaRepository<PolicyDecisionLogEntity, String> {
  List<PolicyDecisionLogEntity> findByRiskDecisionId(String riskDecisionId);
  List<PolicyDecisionLogEntity> findByAgentIdOrderByCreatedAtDesc(String agentId);
}
