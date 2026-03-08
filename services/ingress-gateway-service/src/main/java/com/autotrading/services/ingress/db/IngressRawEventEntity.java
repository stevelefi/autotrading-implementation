package com.autotrading.services.ingress.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ingress_raw_events")
public class IngressRawEventEntity {

  @Id
  @Column(name = "raw_event_id")
  private String rawEventId;

  @Column(name = "ingress_event_id", nullable = false, unique = true)
  private String ingressEventId;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "request_id", nullable = false)
  private String requestId;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "source_protocol")
  private String sourceProtocol;

  @Column(name = "event_intent")
  private String eventIntent;

  @Column(name = "source_event_id")
  private String sourceEventId;

  @Column(name = "agent_id")
  private String agentId;

  @Column(name = "integration_id")
  private String integrationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "principal_json", columnDefinition = "jsonb")
  private String principalJson;

  @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "ingestion_status", nullable = false)
  private String ingestionStatus;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

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
}
