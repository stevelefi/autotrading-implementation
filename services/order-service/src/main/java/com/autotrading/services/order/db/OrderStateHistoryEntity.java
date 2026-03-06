package com.autotrading.services.order.db;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_state_history")
public class OrderStateHistoryEntity {

  @EmbeddedId
  private OrderStateHistoryId id;

  @Column(name = "from_state", nullable = false)
  private String fromState;

  @Column(name = "to_state", nullable = false)
  private String toState;

  @Column(name = "reason")
  private String reason;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected OrderStateHistoryEntity() {}

  public OrderStateHistoryEntity(String orderIntentId, long sequenceNo,
                                  String fromState, String toState, String reason,
                                  String traceId, Instant occurredAt) {
    this.id = new OrderStateHistoryId(orderIntentId, sequenceNo);
    this.fromState = fromState;
    this.toState = toState;
    this.reason = reason;
    this.traceId = traceId;
    this.occurredAt = occurredAt;
  }

  public OrderStateHistoryId getId() { return id; }
  public String getFromState() { return fromState; }
  public String getToState() { return toState; }
  public String getReason() { return reason; }
  public String getTraceId() { return traceId; }
  public Instant getOccurredAt() { return occurredAt; }

  @Embeddable
  public static class OrderStateHistoryId implements Serializable {
    @Column(name = "order_intent_id", nullable = false)
    private String orderIntentId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    protected OrderStateHistoryId() {}

    public OrderStateHistoryId(String orderIntentId, long sequenceNo) {
      this.orderIntentId = orderIntentId;
      this.sequenceNo = sequenceNo;
    }

    public String getOrderIntentId() { return orderIntentId; }
    public long getSequenceNo() { return sequenceNo; }
  }
}
