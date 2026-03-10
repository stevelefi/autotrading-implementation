package com.autotrading.services.eventprocessor.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("routed_trade_events")
public class RoutedTradeEventEntity implements Persistable<String> {

  @Id
  @Column("trade_event_id")
  private String tradeEventId;

  @Column("raw_event_id")
  private String rawEventId;

  @Column("event_id")
  private String eventId;

  @Column("trace_id")
  private String traceId;

  @Column("client_event_id")
  private String clientEventId;

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

  @Transient private boolean isNewEntity;

  protected RoutedTradeEventEntity() {}

  public RoutedTradeEventEntity(
      String tradeEventId,
      String rawEventId,
      String eventId,
      String traceId,
      String clientEventId,
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
    this.eventId = eventId;
    this.traceId = traceId;
    this.clientEventId = clientEventId;
    this.agentId = agentId;
    this.sourceType = sourceType;
    this.sourceEventId = sourceEventId;
    this.canonicalPayloadJson = canonicalPayloadJson;
    this.instrumentId = instrumentId;
    this.routeTopic = routeTopic;
    this.routingStatus = routingStatus;
    this.createdAt = createdAt;
    this.routedAt = routedAt;
    this.isNewEntity = true;
  }

  public String getTradeEventId() { return tradeEventId; }
  public String getRawEventId() { return rawEventId; }
  public String getEventId() { return eventId; }
  public String getTraceId() { return traceId; }
  public String getClientEventId() { return clientEventId; }
  public String getAgentId() { return agentId; }
  public String getSourceType() { return sourceType; }
  public String getSourceEventId() { return sourceEventId; }
  public String getCanonicalPayloadJson() { return canonicalPayloadJson; }
  public String getInstrumentId() { return instrumentId; }
  public String getRouteTopic() { return routeTopic; }
  public String getRoutingStatus() { return routingStatus; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getRoutedAt() { return routedAt; }

  @Override public String getId() { return tradeEventId; }
  @Override public boolean isNew() { return isNewEntity; }
}
