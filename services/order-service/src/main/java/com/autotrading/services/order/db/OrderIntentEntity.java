package com.autotrading.services.order.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_intents")
public class OrderIntentEntity {

  @Id
  @Column(name = "order_intent_id")
  private String orderIntentId;

  @Column(name = "signal_id")
  private String signalId;

  @Column(name = "agent_id", nullable = false)
  private String agentId;

  @Column(name = "instrument_id")
  private String instrumentId;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "side", nullable = false)
  private String side;

  @Column(name = "qty", nullable = false)
  private int qty;

  @Column(name = "order_type", nullable = false)
  private String orderType;

  @Column(name = "time_in_force", nullable = false)
  private String timeInForce;

  @Column(name = "submission_deadline", nullable = false)
  private Instant submissionDeadline;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected OrderIntentEntity() {}

  public OrderIntentEntity(String orderIntentId, String signalId, String agentId,
                            String instrumentId, String idempotencyKey, String side,
                            int qty, String orderType, String timeInForce,
                            Instant submissionDeadline, Instant createdAt) {
    this.orderIntentId = orderIntentId;
    this.signalId = signalId;
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.idempotencyKey = idempotencyKey;
    this.side = side;
    this.qty = qty;
    this.orderType = orderType;
    this.timeInForce = timeInForce;
    this.submissionDeadline = submissionDeadline;
    this.createdAt = createdAt;
  }

  public String getOrderIntentId() { return orderIntentId; }
  public String getSignalId() { return signalId; }
  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getSide() { return side; }
  public int getQty() { return qty; }
  public String getOrderType() { return orderType; }
  public String getTimeInForce() { return timeInForce; }
  public Instant getSubmissionDeadline() { return submissionDeadline; }
  public Instant getCreatedAt() { return createdAt; }
}
