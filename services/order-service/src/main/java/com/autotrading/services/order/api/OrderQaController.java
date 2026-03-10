package com.autotrading.services.order.api;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal QA / automation API for order-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 *
 * <p>Includes a force-state mutation endpoint for negative-path testing (e.g. force
 * an order straight to EXPIRED or REJECTED without waiting for the watchdog).
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class OrderQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public OrderQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ─── Seed ────────────────────────────────────────────────────────────────────

  /**
   * Seed an order_intent + order_ledger pair.
   * Request fields: signalId, agentId, instrumentId, clientEventId, side, qty, orderType,
   *                 timeInForce, initialState (default PENDING_SUBMISSION).
   * Returns: orderIntentId, clientEventId.
   */
  @PostMapping("/seed")
  public Map<String, String> seed(@RequestBody Map<String, Object> req) {
    String orderIntentId  = "qa-order-"  + UUID.randomUUID();
    String clientEventId  = str(req, "clientEventId", "qa-cev-" + UUID.randomUUID());
    String agentId        = str(req, "agentId", "qa-agent");
    String signalId       = str(req, "signalId", "qa-signal-" + UUID.randomUUID());
    String instrumentId   = str(req, "instrumentId", "QA_INSTRUMENT");
    String side           = str(req, "side", "BUY");
    int    qty            = intVal(req, "qty", 1);
    String orderType      = str(req, "orderType", "MKT");
    String timeInForce    = str(req, "timeInForce", "DAY");
    String initialState   = str(req, "initialState", "PENDING_SUBMISSION");
    Timestamp now         = Timestamp.from(Instant.now());
    Timestamp deadline    = Timestamp.from(Instant.now().plusSeconds(60));

    jdbc.update(
        "INSERT INTO order_intents "
        + "(order_intent_id, signal_id, agent_id, instrument_id, client_event_id, "
        + " side, qty, order_type, time_in_force, submission_deadline, created_at) "
        + "VALUES (:id, :signalId, :agentId, :instrumentId, :clientEventId, "
        + " :side, :qty, :orderType, :timeInForce, :deadline, :now)",
        new MapSqlParameterSource()
            .addValue("id",             orderIntentId)
            .addValue("signalId",       signalId)
            .addValue("agentId",        agentId)
            .addValue("instrumentId",   instrumentId)
            .addValue("clientEventId", clientEventId)
            .addValue("side",           side)
            .addValue("qty",            qty)
            .addValue("orderType",      orderType)
            .addValue("timeInForce",    timeInForce)
            .addValue("deadline",       deadline)
            .addValue("now",            now));

    jdbc.update(
        "INSERT INTO order_ledger "
        + "(order_intent_id, state, state_version, submission_deadline, "
        + " last_status_at, updated_at) "
        + "VALUES (:id, :state, 1, :deadline, NULL, :now)",
        new MapSqlParameterSource()
            .addValue("id",       orderIntentId)
            .addValue("state",    initialState)
            .addValue("deadline", deadline)
            .addValue("now",      now));

    return Map.of(
        "order_intent_id", orderIntentId,
        "client_event_id", clientEventId,
        "initial_state",   initialState);
  }

  // ─── State query ─────────────────────────────────────────────────────────────

  @GetMapping("/state")
  public Map<String, Object> state(
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String clientEventId,
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String orderIntentId) {
    if (orderIntentId != null) {
      return Map.of(
          "order_intent",       jdbc.queryForList(
              "SELECT * FROM order_intents WHERE order_intent_id = :v",
              Map.of("v", orderIntentId)),
          "order_ledger",       jdbc.queryForList(
              "SELECT * FROM order_ledger WHERE order_intent_id = :v",
              Map.of("v", orderIntentId)),
          "order_state_history", jdbc.queryForList(
              "SELECT * FROM order_state_history WHERE order_intent_id = :v "
              + "ORDER BY sequence_no",
              Map.of("v", orderIntentId)));
    }
    if (clientEventId != null) {
      return Map.of("order_intents", jdbc.queryForList(
          "SELECT * FROM order_intents WHERE client_event_id = :v",
          Map.of("v", clientEventId)));
    }
    if (agentId != null) {
      return Map.of("order_intents", jdbc.queryForList(
          "SELECT * FROM order_intents WHERE agent_id = :v", Map.of("v", agentId)));
    }
    return Map.of();
  }

  // ─── Force-state mutation ────────────────────────────────────────────────────

  /**
   * Force an order to a target FSM state, bypassing guards. Intended for negative-path
   * testing (e.g. force EXPIRED to test watchdog recovery, force REJECTED to test abort path).
   * Body: { "targetState": "EXPIRED" }
   */
  @PostMapping("/order/{orderId}/force-state")
  public ResponseEntity<Map<String, Object>> forceState(
      @PathVariable String orderId,
      @RequestBody Map<String, Object> req) {
    String targetState = str(req, "targetState", null);
    if (targetState == null) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "targetState is required"));
    }
    int rows = jdbc.update(
        "UPDATE order_ledger "
        + "SET state = :targetState, state_version = state_version + 1, "
        + "    last_status_at = :now, updated_at = :now "
        + "WHERE order_intent_id = :orderId",
        new MapSqlParameterSource()
            .addValue("targetState", targetState)
            .addValue("orderId",     orderId)
            .addValue("now",         Timestamp.from(Instant.now())));
    if (rows == 0) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(Map.of(
        "order_intent_id", orderId,
        "forced_state",    targetState,
        "rows_updated",    rows));
  }

  // ─── Teardown ────────────────────────────────────────────────────────────────

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String testRunPrefix) {
    int deleted = 0;
    if (agentId != null) {
      deleted += jdbc.update(
          "DELETE FROM order_state_history WHERE order_intent_id IN "
          + "(SELECT order_intent_id FROM order_intents WHERE agent_id = :v)",
          Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM order_ledger WHERE order_intent_id IN "
          + "(SELECT order_intent_id FROM order_intents WHERE agent_id = :v)",
          Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM order_intents WHERE agent_id = :v", Map.of("v", agentId));
    }
    if (testRunPrefix != null) {
      deleted += jdbc.update(
          "DELETE FROM order_state_history WHERE order_intent_id IN "
          + "(SELECT order_intent_id FROM order_intents WHERE client_event_id LIKE :v)",
          Map.of("v", testRunPrefix + "%"));
      deleted += jdbc.update(
          "DELETE FROM order_ledger WHERE order_intent_id IN "
          + "(SELECT order_intent_id FROM order_intents WHERE client_event_id LIKE :v)",
          Map.of("v", testRunPrefix + "%"));
      deleted += jdbc.update(
          "DELETE FROM order_intents WHERE client_event_id LIKE :v",
          Map.of("v", testRunPrefix + "%"));
    }
    return Map.of("deleted_rows", deleted);
  }

  // ─── Snapshot ────────────────────────────────────────────────────────────────

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String traceId) {
    // order_intents and order_ledger don't have trace_id; query via idempotency approach
    List<Map<String, Object>> intents = jdbc.queryForList(
        "SELECT oi.*, ol.state, ol.state_version FROM order_intents oi "
        + "LEFT JOIN order_ledger ol ON oi.order_intent_id = ol.order_intent_id "
        + "WHERE oi.client_event_id LIKE :prefix",
        Map.of("prefix", traceId + "%"));
    return Map.of(
        "trace_id",            traceId,
        "order_intents_ledger", intents,
        "order_state_history", intents.isEmpty() ? List.of() :
            jdbc.queryForList(
                "SELECT * FROM order_state_history WHERE order_intent_id IN "
                + "(SELECT order_intent_id FROM order_intents WHERE client_event_id LIKE :prefix) "
                + "ORDER BY order_intent_id, sequence_no",
                Map.of("prefix", traceId + "%")));
  }

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    if (m == null) return defaultVal;
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }

  private static int intVal(Map<String, Object> m, String key, int defaultVal) {
    if (m == null) return defaultVal;
    Object v = m.get(key);
    return v == null ? defaultVal : Integer.parseInt(v.toString());
  }
}
