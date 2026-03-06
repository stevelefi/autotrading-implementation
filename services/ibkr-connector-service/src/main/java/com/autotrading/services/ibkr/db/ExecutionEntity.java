package com.autotrading.services.ibkr.db;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code executions} table.
 * Each row represents a single broker fill (execution report).
 * The {@code exec_id} is globally unique and provides fill-level idempotency.
 */
@Entity
@Table(name = "executions")
public class ExecutionEntity {

  @Id
  @Column(name = "exec_id")
  private String execId;

  @Column(name = "order_intent_id", nullable = false)
  private String orderIntentId;

  @Column(name = "broker_order_id")
  private String brokerOrderId;

  @Column(name = "agent_id")
  private String agentId;

  @Column(name = "instrument_id")
  private String instrumentId;

  @Column(name = "side", nullable = false)
  private String side;

  @Column(name = "fill_qty", nullable = false)
  private int fillQty;

  @Column(name = "fill_price", nullable = false, precision = 18, scale = 6)
  private BigDecimal fillPrice;

  @Column(name = "commission", precision = 18, scale = 6)
  private BigDecimal commission;

  @Column(name = "fill_ts", nullable = false)
  private Instant fillTs;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

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
}
