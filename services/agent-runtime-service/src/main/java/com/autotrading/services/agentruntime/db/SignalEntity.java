package com.autotrading.services.agentruntime.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("signals")
public class SignalEntity {

  @Id
  @Column("signal_id")
  private String signalId;

  @Column("trade_event_id")
  private String tradeEventId;

  @Column("agent_id")
  private String agentId;

  @Column("instrument_id")
  private String instrumentId;

  @Column("idempotency_key")
  private String idempotencyKey;

  @Column("source_type")
  private String sourceType = "AGENT_RUNTIME";

  @Column("source_event_id")
  private String sourceEventId;

  @Column("origin_source_type")
  private String originSourceType;

  @Column("origin_source_event_id")
  private String originSourceEventId;

  @Column("raw_payload_json")
  private String rawPayloadJson;

  @Column("signal_ts")
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
