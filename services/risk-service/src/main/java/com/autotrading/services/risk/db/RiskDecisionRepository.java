package com.autotrading.services.risk.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskDecisionRepository extends JpaRepository<RiskDecisionEntity, String> {
  List<RiskDecisionEntity> findBySignalId(String signalId);
  List<RiskDecisionEntity> findByTraceId(String traceId);
}
