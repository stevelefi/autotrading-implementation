package com.autotrading.services.performance.db;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("pnl_snapshots")
public class PnlSnapshotEntity implements Persistable<String> {

  @Id
  @Column("snapshot_id")
  private String snapshotId;

  @Column("agent_id")
  private String agentId;

  @Column("instrument_id")
  private String instrumentId;

  @Column("snapshot_ts")
  private Instant snapshotTs;

  @Column("unrealized_pnl")
  private BigDecimal unrealizedPnl;

  @Column("realized_pnl")
  private BigDecimal realizedPnl;

  @Column("net_pnl")
  private BigDecimal netPnl;

  @Column("qty")
  private int qty;

  @Column("avg_cost")
  private BigDecimal avgCost;

  @Column("created_at")
  private Instant createdAt;

  @Transient private boolean isNewEntity;

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
    this.isNewEntity = true;
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

  @Override public String getId() { return snapshotId; }
  @Override public boolean isNew() { return isNewEntity; }
}
