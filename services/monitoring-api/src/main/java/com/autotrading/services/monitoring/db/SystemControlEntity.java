package com.autotrading.services.monitoring.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("system_controls")
public class SystemControlEntity {

  @Id
  @Column("control_key")
  private String controlKey;

  @Column("control_value")
  private String controlValue;

  @Column("actor_id")
  private String actorId;

  @Column("trace_id")
  private String traceId;

  @Column("updated_at")
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
