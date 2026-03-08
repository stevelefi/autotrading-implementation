package com.autotrading.services.performance.db;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain POJO for positions rows (composite PK: agent_id + instrument_id).
 * Persistence is handled by PositionWriter via NamedParameterJdbcTemplate
 * using INSERT ... ON CONFLICT (agent_id, instrument_id) DO UPDATE.
 */
public class PositionEntity {

  private String agentId;
  private String instrumentId;
  private int qty;
  private BigDecimal avgCost;
  private BigDecimal realizedPnl;
  private Instant updatedAt;

  public PositionEntity() {}

  public PositionEntity(String agentId, String instrumentId, int qty,
                        BigDecimal avgCost, BigDecimal realizedPnl, Instant updatedAt) {
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.qty = qty;
    this.avgCost = avgCost;
    this.realizedPnl = realizedPnl;
    this.updatedAt = updatedAt;
  }

  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public int getQty() { return qty; }
  public BigDecimal getAvgCost() { return avgCost; }
  public BigDecimal getRealizedPnl() { return realizedPnl; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setQty(int qty) { this.qty = qty; }
  public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
  public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
