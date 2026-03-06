package com.autotrading.services.performance.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory projection of positions from fill events.
 * <p>
 * Deduplication is handled at the consumer layer via {@code ConsumerDeduper}
 * backed by the {@code consumer_inbox} DB table — this service does not
 * maintain its own dedup set so that restarts rebuild correctly from DB state.
 */
public class PerformanceProjectionService {
  private final Map<String, PositionSnapshot> positions = new ConcurrentHashMap<>();

  public boolean applyFill(String agentId, String instrumentId, String execId, int fillQty, BigDecimal fillPrice) {
    String key = agentId + ":" + instrumentId;
    positions.compute(key, (k, current) -> {
      PositionSnapshot prev = current != null ? current : new PositionSnapshot(agentId, instrumentId, 0, BigDecimal.ZERO);
      int newQty = prev.netQty() + fillQty;
      BigDecimal totalCost = prev.avgCost().multiply(BigDecimal.valueOf(prev.netQty()))
          .add(fillPrice.multiply(BigDecimal.valueOf(fillQty)));
      BigDecimal avg = newQty == 0 ? BigDecimal.ZERO : totalCost.divide(BigDecimal.valueOf(newQty), 6, RoundingMode.HALF_UP);
      return new PositionSnapshot(agentId, instrumentId, newQty, avg);
    });
    return true;
  }

  public PositionSnapshot getPosition(String agentId, String instrumentId) {
    return positions.getOrDefault(agentId + ":" + instrumentId, new PositionSnapshot(agentId, instrumentId, 0, BigDecimal.ZERO));
  }

  public record PositionSnapshot(String agentId, String instrumentId, int netQty, BigDecimal avgCost) {
  }
}
