package com.autotrading.services.order.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_ledger")
public class OrderLedgerEntity implements Persistable<String> {

  @Id
  @Column("order_intent_id")
  private String orderIntentId;

  @Column("state")
  private String state;

  @Column("state_version")
  private long stateVersion;

  @Column("submission_deadline")
  private Instant submissionDeadline;

  @Column("last_status_at")
  private Instant lastStatusAt;

  @Column("updated_at")
  private Instant updatedAt;

  @Transient private boolean isNewEntity;

  protected OrderLedgerEntity() {}

  public OrderLedgerEntity(String orderIntentId, String state, long stateVersion,
                            Instant submissionDeadline, Instant lastStatusAt, Instant updatedAt) {
    this.orderIntentId = orderIntentId;
    this.state = state;
    this.stateVersion = stateVersion;
    this.submissionDeadline = submissionDeadline;
    this.lastStatusAt = lastStatusAt;
    this.updatedAt = updatedAt;
    this.isNewEntity = true;
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

  @Override public String getId() { return orderIntentId; }
  @Override public boolean isNew() { return isNewEntity; }
}
