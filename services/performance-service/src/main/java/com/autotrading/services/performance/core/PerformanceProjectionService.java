package com.autotrading.services.performance.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceProjectionService {
  private final Set<String> seenExecIds = ConcurrentHashMap.newKeySet();
  private final Map<String, PositionSnapshot> positions = new ConcurrentHashMap<>();

  public boolean applyFill(String agentId, String instrumentId, String execId, int fillQty, BigDecimal fillPrice) {
    if (!seenExecIds.add(execId)) {
      return false;
    }

    String key = agentId + ":" + instrumentId;
    PositionSnapshot current = positions.getOrDefault(key, new PositionSnapshot(agentId, instrumentId, 0, BigDecimal.ZERO));

    int newQty = current.netQty() + fillQty;
    BigDecimal totalCost = current.avgCost().multiply(BigDecimal.valueOf(current.netQty()))
        .add(fillPrice.multiply(BigDecimal.valueOf(fillQty)));
    BigDecimal avg = newQty == 0 ? BigDecimal.ZERO : totalCost.divide(BigDecimal.valueOf(newQty), 6, RoundingMode.HALF_UP);

    positions.put(key, new PositionSnapshot(agentId, instrumentId, newQty, avg));
    return true;
  }

  public PositionSnapshot getPosition(String agentId, String instrumentId) {
    return positions.getOrDefault(agentId + ":" + instrumentId, new PositionSnapshot(agentId, instrumentId, 0, BigDecimal.ZERO));
  }

  public record PositionSnapshot(String agentId, String instrumentId, int netQty, BigDecimal avgCost) {
  }
}
