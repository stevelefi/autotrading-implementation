package com.autotrading.services.agentruntime.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "signals")
public class SignalEntity {

  @Id
  @Column(name = "signal_id")
  private String signalId;

  @Column(name = "trade_event_id", nullable = false)
  private String tradeEventId;

  @Column(name = "agent_id", nullable = false)
  private String agentId;

  @Column(name = "instrument_id")
  private String instrumentId;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "source_type", nullable = false)
  private String sourceType = "AGENT_RUNTIME";

  @Column(name = "source_event_id")
  private String sourceEventId;

  @Column(name = "origin_source_type", nullable = false)
  private String originSourceType;

  @Column(name = "origin_source_event_id")
  private String originSourceEventId;

  @Column(name = "raw_payload_json", nullable = false, columnDefinition = "TEXT")
  private String rawPayloadJson;

  @Column(name = "signal_ts", nullable = false)
  private Instant signalTs;

  protected SignalEntity() {}

  public SignalEntity(String signalId, String tradeEventId, String agentId, String instrumentId,
                      String idempotencyKey, String sourceType, String sourceEventId,
                      String originSourceType, String originSourceEventId,
                      String rawPayloadJson, Instant signalTs) {
    this.signalId = signalId;
    this.tradeEventId = tradeEventId;
    this.agentId = agentId;
    this.instrumentId = instrumentId;
    this.idempotencyKey = idempotencyKey;
    this.sourceType = sourceType;
    this.sourceEventId = sourceEventId;
    this.originSourceType = originSourceType;
    this.originSourceEventId = originSourceEventId;
    this.rawPayloadJson = rawPayloadJson;
    this.signalTs = signalTs;
  }

  public String getSignalId() { return signalId; }
  public String getTradeEventId() { return tradeEventId; }
  public String getAgentId() { return agentId; }
  public String getInstrumentId() { return instrumentId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getSourceType() { return sourceType; }
  public String getSourceEventId() { return sourceEventId; }
  public String getOriginSourceType() { return originSourceType; }
  public String getOriginSourceEventId() { return originSourceEventId; }
  public String getRawPayloadJson() { return rawPayloadJson; }
  public Instant getSignalTs() { return signalTs; }
}
