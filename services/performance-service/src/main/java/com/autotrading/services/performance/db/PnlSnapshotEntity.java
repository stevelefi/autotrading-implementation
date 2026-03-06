package com.autotrading.services.performance.db;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pnl_snapshots")
public class PnlSnapshotEntity {

  @Id
  @Column(name = "snapshot_id")
  private String snapshotId;

  @Column(name = "agent_id", nullable = false)
  private String agentId;

  @Column(name = "instrument_id", nullable = false)
  private String instrumentId;

  @Column(name = "snapshot_ts", nullable = false)
  private Instant snapshotTs;

  @Column(name = "unrealized_pnl", nullable = false, precision = 18, scale = 6)
  private BigDecimal unrealizedPnl;

  @Column(name = "realized_pnl", nullable = false, precision = 18, scale = 6)
  private BigDecimal realizedPnl;

  @Column(name = "net_pnl", nullable = false, precision = 18, scale = 6)
  private BigDecimal netPnl;

  @Column(name = "qty", nullable = false)
  private int qty;

  @Column(name = "avg_cost", precision = 18, scale = 6)
  private BigDecimal avgCost;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PnlSnapshotEntity() {}

  public PnlSnapshotEntity(String snapshotId, String agentId, String instrumentId,
                            Instant snapshotTs, BigDecimal unrealizedPnl, BigDecimal realizedPnl,
                            BigDecimal netPnl, int qty, BigDecimal avgCost, Instant createdAt) {
    this.snapshotId = snapshotId;
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.snapshotTs = snapshotTs;
    this.unrealizedPnl = unrealizedPnl;
    this.realizedPnl = realizedPnl;
    this.netPnl = netPnl;
    this.qty = qty;
    this.avgCost = avgCost;
    this.createdAt = createdAt;
  }

  public String getSnapshotId() { return snapshotId; }
  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public Instant getSnapshotTs() { return snapshotTs; }
  public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
  public BigDecimal getRealizedPnl() { return realizedPnl; }
  public BigDecimal getNetPnl() { return netPnl; }
  public int getQty() { return qty; }
  public BigDecimal getAvgCost() { return avgCost; }
  public Instant getCreatedAt() { return createdAt; }
}
