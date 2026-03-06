package com.autotrading.services.monitoring.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "system_controls")
public class SystemControlEntity {

  @Id
  @Column(name = "control_key")
  private String controlKey;

  @Column(name = "control_value", nullable = false)
  private String controlValue;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SystemControlEntity() {}

  public SystemControlEntity(String controlKey, String controlValue,
                              String actorId, String traceId, Instant updatedAt) {
    this.controlKey = controlKey;
    this.controlValue = controlValue;
    this.actorId = actorId;
    this.traceId = traceId;
    this.updatedAt = updatedAt;
  }

  public String getControlKey() { return controlKey; }
  public String getControlValue() { return controlValue; }
  public String getActorId() { return actorId; }
  public String getTraceId() { return traceId; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void setControlValue(String controlValue) { this.controlValue = controlValue; }
  public void setActorId(String actorId) { this.actorId = actorId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
