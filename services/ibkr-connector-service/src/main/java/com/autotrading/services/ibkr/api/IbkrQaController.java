package com.autotrading.services.ibkr.api;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
 * Internal QA / automation API for ibkr-connector-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class IbkrQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public IbkrQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ─── Seed ────────────────────────────────────────────────────────────────────

  /**
   * Seed a broker_order row and optionally an execution row.
   * Request fields: orderIntentId, agentId, orderRef, instrumentId, side, qty, status,
   *                 withExecution (boolean), fillQty, fillPrice.
   * Returns: brokerOrderId, executionId (if seeded).
   */
  @PostMapping("/seed")
  public Map<String, Object> seed(@RequestBody Map<String, Object> req) {
    String brokerOrderId = "qa-broker-"  + UUID.randomUUID();
    String orderIntentId = str(req, "orderIntentId", "qa-oi-" + UUID.randomUUID());
    String agentId       = str(req, "agentId",       "qa-agent");
    String orderRef      = str(req, "orderRef",      "QA-" + System.currentTimeMillis());
    String instrumentId  = str(req, "instrumentId",  "QA_INSTRUMENT");
    String side          = str(req, "side",           "BUY");
    int    qty           = intVal(req, "qty",          1);
    String status        = str(req, "status",         "SUBMITTED");
    Timestamp now        = Timestamp.from(Instant.now());

    jdbc.update(
        "INSERT INTO broker_orders "
        + "(broker_order_id, order_intent_id, agent_id, order_ref, instrument_id, "
        + " side, qty, status, updated_at, created_at) "
        + "VALUES (:id, :oi, :agentId, :orderRef, :instr, :side, :qty, :status, :now, :now)",
        new MapSqlParameterSource()
            .addValue("id",      brokerOrderId)
            .addValue("oi",      orderIntentId)
            .addValue("agentId", agentId)
            .addValue("orderRef", orderRef)
            .addValue("instr",   instrumentId)
            .addValue("side",    side)
            .addValue("qty",     qty)
            .addValue("status",  status)
            .addValue("now",     now));

    boolean withExecution = Boolean.parseBoolean(str(req, "withExecution", "false"));
    String executionId = null;
    if (withExecution) {
      executionId = "qa-exec-" + UUID.randomUUID();
      BigDecimal fillQty   = new BigDecimal(str(req, "fillQty",   "1"));
      BigDecimal fillPrice = new BigDecimal(str(req, "fillPrice", "100.00"));
      jdbc.update(
          "INSERT INTO executions "
          + "(execution_id, broker_order_id, order_intent_id, agent_id, instrument_id, "
          + " side, fill_qty, fill_price, fill_ts, created_at) "
          + "VALUES (:execId, :brokerOrderId, :oi, :agentId, :instr, :side, "
          + " :fillQty, :fillPrice, :now, :now)",
          new MapSqlParameterSource()
              .addValue("execId",       executionId)
              .addValue("brokerOrderId", brokerOrderId)
              .addValue("oi",           orderIntentId)
              .addValue("agentId",      agentId)
              .addValue("instr",        instrumentId)
              .addValue("side",         side)
              .addValue("fillQty",      fillQty)
              .addValue("fillPrice",    fillPrice)
              .addValue("now",          now));
    }

    return executionId != null
        ? Map.of("broker_order_id", brokerOrderId, "execution_id", executionId)
        : Map.of("broker_order_id", brokerOrderId);
  }

  // ─── State query ─────────────────────────────────────────────────────────────

  @GetMapping("/state")
  public Map<String, Object> state(
      @RequestParam(required = false) String orderIntentId,
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String orderRef) {
    if (orderIntentId != null) {
      return Map.of(
          "broker_orders", jdbc.queryForList(
              "SELECT * FROM broker_orders WHERE order_intent_id = :v",
              Map.of("v", orderIntentId)),
          "executions", jdbc.queryForList(
              "SELECT * FROM executions WHERE order_intent_id = :v ORDER BY fill_ts",
              Map.of("v", orderIntentId)));
    }
    if (agentId != null) {
      return Map.of(
          "broker_orders", jdbc.queryForList(
              "SELECT * FROM broker_orders WHERE agent_id = :v", Map.of("v", agentId)));
    }
    if (orderRef != null) {
      return Map.of(
          "broker_orders", jdbc.queryForList(
              "SELECT * FROM broker_orders WHERE order_ref = :v", Map.of("v", orderRef)));
    }
    return Map.of();
  }

  // ─── Teardown ────────────────────────────────────────────────────────────────

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String orderIntentId) {
    int deleted = 0;
    if (agentId != null) {
      deleted += jdbc.update(
          "DELETE FROM executions WHERE agent_id = :v", Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM broker_orders WHERE agent_id = :v", Map.of("v", agentId));
    }
    if (orderIntentId != null) {
      deleted += jdbc.update(
          "DELETE FROM executions WHERE order_intent_id = :v", Map.of("v", orderIntentId));
      deleted += jdbc.update(
          "DELETE FROM broker_orders WHERE order_intent_id = :v", Map.of("v", orderIntentId));
    }
    return Map.of("deleted_rows", deleted);
  }

  // ─── Snapshot ────────────────────────────────────────────────────────────────

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String agentId) {
    return Map.of(
        "agent_id",      agentId,
        "broker_orders", jdbc.queryForList(
            "SELECT * FROM broker_orders WHERE agent_id = :v", Map.of("v", agentId)),
        "executions",    jdbc.queryForList(
            "SELECT * FROM executions WHERE agent_id = :v ORDER BY fill_ts", Map.of("v", agentId)));
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
