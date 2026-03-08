package com.autotrading.services.ibkr.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("broker_orders")
public class BrokerOrderEntity implements Persistable<String> {

  @Id
  @Column("broker_order_id")
  private String brokerOrderId;

  @Column("order_intent_id")
  private String orderIntentId;

  @Column("order_ref")
  private String orderRef;

  @Column("perm_id")
  private String permId;

  @Column("agent_id")
  private String agentId;

  @Column("instrument_id")
  private String instrumentId;

  @Column("side")
  private String side;

  @Column("qty")
  private int qty;

  @Column("order_type")
  private String orderType;

  @Column("status")
  private String status;

  @Column("submitted_at")
  private Instant submittedAt;

  @Column("updated_at")
  private Instant updatedAt;

  @Transient private boolean isNewEntity;

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
    this.isNewEntity = true;
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

  @Override public String getId() { return brokerOrderId; }
  @Override public boolean isNew() { return isNewEntity; }
}
