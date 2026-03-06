package com.autotrading.services.performance.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PnlSnapshotRepository extends JpaRepository<PnlSnapshotEntity, String> {
  List<PnlSnapshotEntity> findByAgentIdAndInstrumentIdOrderBySnapshotTsDesc(
      String agentId, String instrumentId);
}
