package com.autotrading.services.eventprocessor.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("routed_trade_events")
public class RoutedTradeEventEntity {

  @Id
  @Column("trade_event_id")
  private String tradeEventId;

  @Column("raw_event_id")
  private String rawEventId;

  @Column("ingress_event_id")
  private String ingressEventId;

  @Column("trace_id")
  private String traceId;

  @Column("idempotency_key")
  private String idempotencyKey;

  @Column("agent_id")
  private String agentId;

  @Column("source_type")
  private String sourceType;

  @Column("source_event_id")
  private String sourceEventId;

  @Column("canonical_payload_json")
  private String canonicalPayloadJson;

  @Column("instrument_id")
  private String instrumentId;

  @Column("route_topic")
  private String routeTopic;

  @Column("routing_status")
  private String routingStatus;

  @Column("created_at")
  private Instant createdAt;

  @Column("routed_at")
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
