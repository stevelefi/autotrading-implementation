package com.autotrading.services.risk.db;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface RiskDecisionRepository extends ListCrudRepository<RiskDecisionEntity, String> {
  List<RiskDecisionEntity> findBySignalId(String signalId);
  List<RiskDecisionEntity> findByTraceId(String traceId);
}
