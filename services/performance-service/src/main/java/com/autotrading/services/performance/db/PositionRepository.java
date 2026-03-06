package com.autotrading.services.performance.db;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, PositionEntity.PositionId> {
  List<PositionEntity> findByIdAgentId(String agentId);
  Optional<PositionEntity> findByIdAgentIdAndIdInstrumentId(String agentId, String instrumentId);
}
