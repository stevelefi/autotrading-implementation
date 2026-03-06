package com.autotrading.services.eventprocessor.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "routed_trade_events")
public class RoutedTradeEventEntity {

  @Id
  @Column(name = "trade_event_id")
  private String tradeEventId;

  @Column(name = "raw_event_id", nullable = false, unique = true)
  private String rawEventId;

  @Column(name = "ingress_event_id", nullable = false)
  private String ingressEventId;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "agent_id")
  private String agentId;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "source_event_id")
  private String sourceEventId;

  @Column(name = "canonical_payload_json", nullable = false, columnDefinition = "TEXT")
  private String canonicalPayloadJson;

  @Column(name = "instrument_id")
  private String instrumentId;

  @Column(name = "route_topic")
  private String routeTopic;

  @Column(name = "routing_status")
  private String routingStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "routed_at", nullable = false)
  private Instant routedAt;

  protected RoutedTradeEventEntity() {}

  public RoutedTradeEventEntity(
      String tradeEventId,
      String rawEventId,
      String ingressEventId,
      String traceId,
      String idempotencyKey,
      String agentId,
      String sourceType,
      String sourceEventId,
      String canonicalPayloadJson,
      String instrumentId,
      String routeTopic,
      String routingStatus,
      Instant createdAt,
      Instant routedAt) {
    this.tradeEventId = tradeEventId;
    this.rawEventId = rawEventId;
    this.ingressEventId = ingressEventId;
    this.traceId = traceId;
    this.idempotencyKey = idempotencyKey;
    this.agentId = agentId;
    this.sourceType = sourceType;
    this.sourceEventId = sourceEventId;
    this.canonicalPayloadJson = canonicalPayloadJson;
    this.instrumentId = instrumentId;
    this.routeTopic = routeTopic;
    this.routingStatus = routingStatus;
    this.createdAt = createdAt;
    this.routedAt = routedAt;
  }

  public String getTradeEventId() { return tradeEventId; }
  public String getRawEventId() { return rawEventId; }
  public String getIngressEventId() { return ingressEventId; }
  public String getTraceId() { return traceId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getAgentId() { return agentId; }
  public String getSourceType() { return sourceType; }
  public String getSourceEventId() { return sourceEventId; }
  public String getCanonicalPayloadJson() { return canonicalPayloadJson; }
  public String getInstrumentId() { return instrumentId; }
  public String getRouteTopic() { return routeTopic; }
  public String getRoutingStatus() { return routingStatus; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getRoutedAt() { return routedAt; }
}
