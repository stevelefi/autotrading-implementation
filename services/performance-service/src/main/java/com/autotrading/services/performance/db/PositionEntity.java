package com.autotrading.services.performance.db;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "positions")
public class PositionEntity {

  @EmbeddedId
  private PositionId id;

  @Column(name = "qty", nullable = false)
  private int qty;

  @Column(name = "avg_cost", precision = 18, scale = 6)
  private BigDecimal avgCost;

  @Column(name = "realized_pnl", nullable = false, precision = 18, scale = 6)
  private BigDecimal realizedPnl;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PositionEntity() {}

  public PositionEntity(String agentId, String instrumentId, int qty,
                        BigDecimal avgCost, BigDecimal realizedPnl, Instant updatedAt) {
    this.id = new PositionId(agentId, instrumentId);
    this.qty = qty;
    this.avgCost = avgCost;
    this.realizedPnl = realizedPnl;
    this.updatedAt = updatedAt;
  }

  public PositionId getId() { return id; }
  public String getAgentId() { return id.getAgentId(); }
  public String getInstrumentId() { return id.getInstrumentId(); }
  public int getQty() { return qty; }
  public BigDecimal getAvgCost() { return avgCost; }
  public BigDecimal getRealizedPnl() { return realizedPnl; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setQty(int qty) { this.qty = qty; }
  public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
  public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  @Embeddable
  public static class PositionId implements Serializable {
    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "instrument_id", nullable = false)
    private String instrumentId;

    protected PositionId() {}

    public PositionId(String agentId, String instrumentId) {
      this.agentId = agentId;
      this.instrumentId = instrumentId;
    }

    public String getAgentId() { return agentId; }
    public String getInstrumentId() { return instrumentId; }
  }
}
