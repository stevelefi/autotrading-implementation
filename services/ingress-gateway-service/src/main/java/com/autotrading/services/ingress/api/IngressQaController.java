package com.autotrading.services.ingress.api;

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
 * Internal QA / automation API for ingress-gateway-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST  /internal/qa/seed              – insert a controlled ingress_raw_events row
 *   <li>GET   /internal/qa/state             – query rows by traceId / idempotencyKey / agentId
 *   <li>DELETE /internal/qa/data             – delete rows by agentId or idempotency-key prefix
 *   <li>GET   /internal/qa/snapshot          – all ingress rows for a trace (for assertion)
 * </ul>
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class IngressQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public IngressQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ─── Seed ───────────────────────────────────────────────────────────────────

  /**
   * Insert a controlled ingress_raw_events row.
   * Request fields: traceId (required), agentId, idempotencyKey, sourceType, payloadJson.
   * Returns the generated rawEventId and ingressEventId.
   */
  @PostMapping("/seed")
  public Map<String, String> seed(@RequestBody Map<String, Object> req) {
    String rawEventId     = "qa-raw-"     + UUID.randomUUID();
    String ingressEventId = "qa-ingress-" + UUID.randomUUID();
    String traceId        = str(req, "traceId", "qa-trace-" + UUID.randomUUID());
    String idempotencyKey = str(req, "idempotencyKey", "qa-idem-" + UUID.randomUUID());
    String agentId        = str(req, "agentId", "qa-agent");
    String sourceType     = str(req, "sourceType", "QA");
    String payloadJson    = str(req, "payloadJson", "{\"qa\":true}");

    jdbc.update(
        "INSERT INTO ingress_raw_events "
        + "(raw_event_id, ingress_event_id, trace_id, request_id, idempotency_key, "
        + " source_type, source_protocol, event_intent, source_event_id, agent_id, "
        + " integration_id, principal_json, payload_json, ingestion_status, received_at) "
        + "VALUES (:rawEventId, :ingressEventId, :traceId, :requestId, :idempotencyKey, "
        + " :sourceType, 'QA', 'QA_SEED', NULL, :agentId, "
        + " NULL, NULL, :payloadJson, 'PROCESSED', :now)",
        new MapSqlParameterSource()
            .addValue("rawEventId",     rawEventId)
            .addValue("ingressEventId", ingressEventId)
            .addValue("traceId",        traceId)
            .addValue("requestId",      "qa-req-" + UUID.randomUUID())
            .addValue("idempotencyKey", idempotencyKey)
            .addValue("sourceType",     sourceType)
            .addValue("agentId",        agentId)
            .addValue("payloadJson",    payloadJson)
            .addValue("now",            Timestamp.from(Instant.now())));

    return Map.of(
        "raw_event_id",     rawEventId,
        "ingress_event_id", ingressEventId,
        "trace_id",         traceId,
        "idempotency_key",  idempotencyKey);
  }

  // ─── State query ─────────────────────────────────────────────────────────────

  @GetMapping("/state")
  public List<Map<String, Object>> state(
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String idempotencyKey,
      @RequestParam(required = false) String agentId) {
    if (traceId != null) {
      return jdbc.queryForList(
          "SELECT * FROM ingress_raw_events WHERE trace_id = :v",
          Map.of("v", traceId));
    }
    if (idempotencyKey != null) {
      return jdbc.queryForList(
          "SELECT * FROM ingress_raw_events WHERE idempotency_key = :v",
          Map.of("v", idempotencyKey));
    }
    if (agentId != null) {
      return jdbc.queryForList(
          "SELECT * FROM ingress_raw_events WHERE agent_id = :v",
          Map.of("v", agentId));
    }
    return List.of();
  }

  // ─── Teardown ────────────────────────────────────────────────────────────────

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String testRunPrefix) {
    int deleted = 0;
    if (agentId != null) {
      deleted += jdbc.update(
          "DELETE FROM ingress_raw_events WHERE agent_id = :v", Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM ingress_errors WHERE trace_id IN "
          + "(SELECT trace_id FROM ingress_raw_events WHERE agent_id = :v)",
          Map.of("v", agentId));
    }
    if (testRunPrefix != null) {
      deleted += jdbc.update(
          "DELETE FROM ingress_raw_events WHERE idempotency_key LIKE :v",
          Map.of("v", testRunPrefix + "%"));
    }
    return Map.of("deleted_rows", deleted);
  }

  // ─── Snapshot ────────────────────────────────────────────────────────────────

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String traceId) {
    return Map.of(
        "trace_id",           traceId,
        "ingress_raw_events", jdbc.queryForList(
            "SELECT * FROM ingress_raw_events WHERE trace_id = :v", Map.of("v", traceId)),
        "ingress_errors",     jdbc.queryForList(
            "SELECT * FROM ingress_errors WHERE trace_id = :v", Map.of("v", traceId)));
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }
}
