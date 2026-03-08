package com.autotrading.services.performance.db;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface PnlSnapshotRepository extends ListCrudRepository<PnlSnapshotEntity, String> {
  List<PnlSnapshotEntity> findByAgentIdAndInstrumentIdOrderBySnapshotTsDesc(
      String agentId, String instrumentId);
}
