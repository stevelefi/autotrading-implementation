package com.autotrading.services.agentruntime.api;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal QA / automation API for agent-runtime-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class AgentRuntimeQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public AgentRuntimeQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostMapping("/seed")
  public Map<String, String> seed(@RequestBody Map<String, Object> req) {
    String signalId       = "qa-signal-" + UUID.randomUUID();
    String traceId        = str(req, "traceId", "qa-trace-" + UUID.randomUUID());
    String idempotencyKey = str(req, "idempotencyKey", "qa-idem-" + UUID.randomUUID());
    String agentId        = str(req, "agentId", "qa-agent");
    String instrumentId   = str(req, "instrumentId", "QA_INSTRUMENT");
    String tradeEventId   = str(req, "tradeEventId", "qa-trade-" + UUID.randomUUID());

    jdbc.update(
        "INSERT INTO signals "
        + "(signal_id, trade_event_id, agent_id, instrument_id, idempotency_key, "
        + " source_type, source_event_id, origin_source_type, origin_source_event_id, "
        + " raw_payload_json, signal_ts) "
        + "VALUES (:signalId, :tradeEventId, :agentId, :instrumentId, :idempotencyKey, "
        + " 'AGENT_RUNTIME', NULL, 'QA', NULL, "
        + " :rawPayload, :now)",
        new MapSqlParameterSource()
            .addValue("signalId",       signalId)
            .addValue("tradeEventId",   tradeEventId)
            .addValue("agentId",        agentId)
            .addValue("instrumentId",   instrumentId)
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("rawPayload",     "{\"qa\":true}")
            .addValue("now",            Timestamp.from(Instant.now())));

    return Map.of(
        "signal_id",       signalId,
        "trace_id",        traceId,
        "idempotency_key", idempotencyKey,
        "agent_id",        agentId);
  }

  @GetMapping("/state")
  public List<Map<String, Object>> state(
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String idempotencyKey,
      @RequestParam(required = false) String agentId) {
    if (idempotencyKey != null) {
      return jdbc.queryForList(
          "SELECT * FROM signals WHERE idempotency_key = :v", Map.of("v", idempotencyKey));
    }
    if (agentId != null) {
      return jdbc.queryForList(
          "SELECT * FROM signals WHERE agent_id = :v", Map.of("v", agentId));
    }
    return List.of();
  }

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String testRunPrefix) {
    int deleted = 0;
    if (agentId != null) {
      deleted += jdbc.update(
          "DELETE FROM signals WHERE agent_id = :v", Map.of("v", agentId));
    }
    if (testRunPrefix != null) {
      deleted += jdbc.update(
          "DELETE FROM signals WHERE idempotency_key LIKE :v",
          Map.of("v", testRunPrefix + "%"));
    }
    return Map.of("deleted_rows", deleted);
  }

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String traceId) {
    return Map.of(
        "trace_id", traceId,
        "signals",  jdbc.queryForList(
            "SELECT * FROM signals WHERE trade_event_id IN "
            + "(SELECT trade_event_id FROM routed_trade_events WHERE trace_id = :v)",
            Map.of("v", traceId)));
  }

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }
}
