package com.autotrading.services.eventprocessor.api;

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
 * Internal QA / automation API for event-processor-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class EventProcessorQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public EventProcessorQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostMapping("/seed")
  public Map<String, String> seed(@RequestBody Map<String, Object> req) {
    String tradeEventId   = "qa-trade-"   + UUID.randomUUID();
    String traceId        = str(req, "traceId", "qa-trace-" + UUID.randomUUID());
    String idempotencyKey = str(req, "idempotencyKey", "qa-idem-" + UUID.randomUUID());
    String agentId        = str(req, "agentId", "qa-agent");
    String rawEventId     = str(req, "rawEventId", "qa-raw-" + UUID.randomUUID());

    jdbc.update(
        "INSERT INTO routed_trade_events "
        + "(trade_event_id, raw_event_id, ingress_event_id, trace_id, idempotency_key, "
        + " agent_id, source_type, source_event_id, canonical_payload_json, "
        + " instrument_id, route_topic, routing_status, created_at, routed_at) "
        + "VALUES (:tradeEventId, :rawEventId, :ingressEventId, :traceId, :idempotencyKey, "
        + " :agentId, 'QA', NULL, '{\"qa\":true}', "
        + " 'QA_INSTRUMENT', 'qa.signals.v1', 'ROUTED', :now, :now)",
        new MapSqlParameterSource()
            .addValue("tradeEventId",   tradeEventId)
            .addValue("rawEventId",     rawEventId)
            .addValue("ingressEventId", "qa-ingress-" + UUID.randomUUID())
            .addValue("traceId",        traceId)
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("agentId",        agentId)
            .addValue("now",            Timestamp.from(Instant.now())));

    return Map.of(
        "trade_event_id",  tradeEventId,
        "trace_id",        traceId,
        "idempotency_key", idempotencyKey);
  }

  @GetMapping("/state")
  public List<Map<String, Object>> state(
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String idempotencyKey,
      @RequestParam(required = false) String agentId) {
    if (traceId != null) {
      return jdbc.queryForList(
          "SELECT * FROM routed_trade_events WHERE trace_id = :v", Map.of("v", traceId));
    }
    if (idempotencyKey != null) {
      return jdbc.queryForList(
          "SELECT * FROM routed_trade_events WHERE idempotency_key = :v",
          Map.of("v", idempotencyKey));
    }
    if (agentId != null) {
      return jdbc.queryForList(
          "SELECT * FROM routed_trade_events WHERE agent_id = :v", Map.of("v", agentId));
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
          "DELETE FROM routed_trade_events WHERE agent_id = :v", Map.of("v", agentId));
    }
    if (testRunPrefix != null) {
      deleted += jdbc.update(
          "DELETE FROM routed_trade_events WHERE idempotency_key LIKE :v",
          Map.of("v", testRunPrefix + "%"));
    }
    return Map.of("deleted_rows", deleted);
  }

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String traceId) {
    return Map.of(
        "trace_id",            traceId,
        "routed_trade_events", jdbc.queryForList(
            "SELECT * FROM routed_trade_events WHERE trace_id = :v", Map.of("v", traceId)));
  }

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }
}
