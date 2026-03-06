package com.autotrading.services.ibkr.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "broker_orders")
public class BrokerOrderEntity {

  @Id
  @Column(name = "broker_order_id")
  private String brokerOrderId;

  @Column(name = "order_intent_id", nullable = false)
  private String orderIntentId;

  @Column(name = "order_ref", nullable = false, unique = true)
  private String orderRef;

  @Column(name = "perm_id", unique = true)
  private String permId;

  @Column(name = "agent_id", nullable = false)
  private String agentId;

  @Column(name = "instrument_id")
  private String instrumentId;

  @Column(name = "side", nullable = false)
  private String side;

  @Column(name = "qty", nullable = false)
  private int qty;

  @Column(name = "order_type", nullable = false)
  private String orderType;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BrokerOrderEntity() {}

  public BrokerOrderEntity(String brokerOrderId, String orderIntentId, String orderRef,
                            String permId, String agentId, String instrumentId, String side,
                            int qty, String orderType, String status,
                            Instant submittedAt, Instant updatedAt) {
    this.brokerOrderId = brokerOrderId;
    this.orderIntentId = orderIntentId;
    this.orderRef = orderRef;
    this.permId = permId;
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.side = side;
    this.qty = qty;
    this.orderType = orderType;
    this.status = status;
    this.submittedAt = submittedAt;
    this.updatedAt = updatedAt;
  }

  public String getBrokerOrderId() { return brokerOrderId; }
  public String getOrderIntentId() { return orderIntentId; }
  public String getOrderRef() { return orderRef; }
  public String getPermId() { return permId; }
  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public String getSide() { return side; }
  public int getQty() { return qty; }
  public String getOrderType() { return orderType; }
  public String getStatus() { return status; }
  public Instant getSubmittedAt() { return submittedAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setStatus(String status) { this.status = status; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
