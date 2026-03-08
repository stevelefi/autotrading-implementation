package com.autotrading.services.ibkr.db;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Aggregate for the {@code executions} table.
 * Each row represents a single broker fill (execution report).
 * The {@code exec_id} is globally unique and provides fill-level idempotency.
 */
@Table("executions")
public class ExecutionEntity implements Persistable<String> {

  @Id
  @Column("exec_id")
  private String execId;

  @Column("order_intent_id")
  private String orderIntentId;

  @Column("broker_order_id")
  private String brokerOrderId;

  @Column("agent_id")
  private String agentId;

  @Column("instrument_id")
  private String instrumentId;

  @Column("side")
  private String side;

  @Column("fill_qty")
  private int fillQty;

  @Column("fill_price")
  private BigDecimal fillPrice;

  @Column("commission")
  private BigDecimal commission;

  @Column("fill_ts")
  private Instant fillTs;

  @Column("created_at")
  private Instant createdAt;

  @Transient private boolean isNewEntity;

  protected ExecutionEntity() {}

  public ExecutionEntity(String execId, String orderIntentId, String brokerOrderId,
                          String agentId, String instrumentId, String side,
                          int fillQty, BigDecimal fillPrice, BigDecimal commission,
                          Instant fillTs, Instant createdAt) {
    this.execId = execId;
    this.orderIntentId = orderIntentId;
    this.brokerOrderId = brokerOrderId;
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.side = side;
    this.fillQty = fillQty;
    this.fillPrice = fillPrice;
    this.commission = commission;
    this.fillTs = fillTs;
    this.createdAt = createdAt;
    this.isNewEntity = true;
  }

  public String getExecId() { return execId; }
  public String getOrderIntentId() { return orderIntentId; }
  public String getBrokerOrderId() { return brokerOrderId; }
  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public String getSide() { return side; }
  public int getFillQty() { return fillQty; }
  public BigDecimal getFillPrice() { return fillPrice; }
  public BigDecimal getCommission() { return commission; }
  public Instant getFillTs() { return fillTs; }
  public Instant getCreatedAt() { return createdAt; }

  @Override public String getId() { return execId; }
  @Override public boolean isNew() { return isNewEntity; }
}
