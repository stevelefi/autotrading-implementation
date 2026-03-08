package com.autotrading.services.ingress.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("ingress_raw_events")
public class IngressRawEventEntity implements Persistable<String> {

  @Id
  @Column("raw_event_id")
  private String rawEventId;

  @Column("ingress_event_id")
  private String ingressEventId;

  @Column("trace_id")
  private String traceId;

  @Column("request_id")
  private String requestId;

  @Column("idempotency_key")
  private String idempotencyKey;

  @Column("source_type")
  private String sourceType;

  @Column("source_protocol")
  private String sourceProtocol;

  @Column("event_intent")
  private String eventIntent;

  @Column("source_event_id")
  private String sourceEventId;

  @Column("agent_id")
  private String agentId;

  @Column("integration_id")
  private String integrationId;

  @Column("principal_json")
  private String principalJson;

  @Column("payload_json")
  private String payloadJson;

  @Column("ingestion_status")
  private String ingestionStatus;

  @Column("received_at")
  private Instant receivedAt;

  @Transient private boolean isNewEntity;

  protected IngressRawEventEntity() {}

  public IngressRawEventEntity(
      String rawEventId,
      String ingressEventId,
      String traceId,
      String requestId,
      String idempotencyKey,
      String sourceType,
      String sourceProtocol,
      String eventIntent,
      String sourceEventId,
      String agentId,
      String integrationId,
      String principalJson,
      String payloadJson,
      String ingestionStatus,
      Instant receivedAt) {
    this.rawEventId = rawEventId;
    this.ingressEventId = ingressEventId;
    this.traceId = traceId;
    this.requestId = requestId;
    this.idempotencyKey = idempotencyKey;
    this.sourceType = sourceType;
    this.sourceProtocol = sourceProtocol;
    this.eventIntent = eventIntent;
    this.sourceEventId = sourceEventId;
    this.agentId = agentId;
    this.integrationId = integrationId;
    this.principalJson = principalJson;
    this.payloadJson = payloadJson;
    this.ingestionStatus = ingestionStatus;
    this.receivedAt = receivedAt;
    this.isNewEntity = true;
  }

  public String getRawEventId() { return rawEventId; }
  public String getIngressEventId() { return ingressEventId; }
  public String getTraceId() { return traceId; }
  public String getRequestId() { return requestId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getSourceType() { return sourceType; }
  public String getSourceProtocol() { return sourceProtocol; }
  public String getEventIntent() { return eventIntent; }
  public String getSourceEventId() { return sourceEventId; }
  public String getAgentId() { return agentId; }
  public String getIntegrationId() { return integrationId; }
  public String getPrincipalJson() { return principalJson; }
  public String getPayloadJson() { return payloadJson; }
  public String getIngestionStatus() { return ingestionStatus; }
  public Instant getReceivedAt() { return receivedAt; }

  @Override public String getId() { return rawEventId; }
  @Override public boolean isNew() { return isNewEntity; }
}
