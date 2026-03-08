package com.autotrading.services.order.db;

import java.time.Instant;

/**
 * Plain POJO for order_state_history rows (composite PK: order_intent_id + sequence_no).
 * Persistence is handled by OrderStateHistoryWriter via NamedParameterJdbcTemplate.
 */
public class OrderStateHistoryEntity {

  private String orderIntentId;
  private long sequenceNo;
  private String fromState;
  private String toState;
  private String reason;
  private String traceId;
  private Instant occurredAt;

  public OrderStateHistoryEntity() {}

  public OrderStateHistoryEntity(String orderIntentId, long sequenceNo,
                                  String fromState, String toState, String reason,
                                  String traceId, Instant occurredAt) {
    this.orderIntentId = orderIntentId;
    this.sequenceNo = sequenceNo;
    this.fromState = fromState;
    this.toState = toState;
    this.reason = reason;
    this.traceId = traceId;
    this.occurredAt = occurredAt;
  }

  public String getOrderIntentId() { return orderIntentId; }
  public long getSequenceNo() { return sequenceNo; }
  public String getFromState() { return fromState; }
  public String getToState() { return toState; }
  public String getReason() { return reason; }
  public String getTraceId() { return traceId; }
  public Instant getOccurredAt() { return occurredAt; }
}
