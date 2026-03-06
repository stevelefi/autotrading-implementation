package com.autotrading.services.order.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_ledger")
public class OrderLedgerEntity {

  @Id
  @Column(name = "order_intent_id")
  private String orderIntentId;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "state_version", nullable = false)
  private long stateVersion;

  @Column(name = "submission_deadline", nullable = false)
  private Instant submissionDeadline;

  @Column(name = "last_status_at")
  private Instant lastStatusAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OrderLedgerEntity() {}

  public OrderLedgerEntity(String orderIntentId, String state, long stateVersion,
                            Instant submissionDeadline, Instant lastStatusAt, Instant updatedAt) {
    this.orderIntentId = orderIntentId;
    this.state = state;
    this.stateVersion = stateVersion;
    this.submissionDeadline = submissionDeadline;
    this.lastStatusAt = lastStatusAt;
    this.updatedAt = updatedAt;
  }

  public String getOrderIntentId() { return orderIntentId; }
  public String getState() { return state; }
  public long getStateVersion() { return stateVersion; }
  public Instant getSubmissionDeadline() { return submissionDeadline; }
  public Instant getLastStatusAt() { return lastStatusAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setState(String state) { this.state = state; }
  public void setStateVersion(long stateVersion) { this.stateVersion = stateVersion; }
  public void setLastStatusAt(Instant lastStatusAt) { this.lastStatusAt = lastStatusAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
