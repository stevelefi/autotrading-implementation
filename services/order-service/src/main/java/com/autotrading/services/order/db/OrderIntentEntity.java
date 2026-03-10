package com.autotrading.services.order.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_intents")
public class OrderIntentEntity implements Persistable<String> {

  @Id
  @Column("order_intent_id")
  private String orderIntentId;

  @Column("signal_id")
  private String signalId;

  @Column("agent_id")
  private String agentId;

  @Column("instrument_id")
  private String instrumentId;

  @Column("client_event_id")
  private String clientEventId;

  @Column("side")
  private String side;

  @Column("qty")
  private int qty;

  @Column("order_type")
  private String orderType;

  @Column("time_in_force")
  private String timeInForce;

  @Column("submission_deadline")
  private Instant submissionDeadline;

  @Column("created_at")
  private Instant createdAt;

  @Transient private boolean isNewEntity;

  protected OrderIntentEntity() {}

  public OrderIntentEntity(String orderIntentId, String signalId, String agentId,
                            String instrumentId, String clientEventId, String side,
                            int qty, String orderType, String timeInForce,
                            Instant submissionDeadline, Instant createdAt) {
    this.orderIntentId = orderIntentId;
    this.signalId = signalId;
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.clientEventId = clientEventId;
    this.side = side;
    this.qty = qty;
    this.orderType = orderType;
    this.timeInForce = timeInForce;
    this.submissionDeadline = submissionDeadline;
    this.createdAt = createdAt;
    this.isNewEntity = true;
  }

  public String getOrderIntentId() { return orderIntentId; }
  public String getSignalId() { return signalId; }
  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public String getClientEventId() { return clientEventId; }
  public String getSide() { return side; }
  public int getQty() { return qty; }
  public String getOrderType() { return orderType; }
  public String getTimeInForce() { return timeInForce; }
  public Instant getSubmissionDeadline() { return submissionDeadline; }
  public Instant getCreatedAt() { return createdAt; }

  @Override public String getId() { return orderIntentId; }
  @Override public boolean isNew() { return isNewEntity; }
}
