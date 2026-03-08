package com.autotrading.services.risk.api;

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
 * Internal QA / automation API for risk-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 *
 * <p>The force-state mutation for "force a risk decision" is intentionally not
 * provided — risk decisions are immutable once written. Use seed instead.
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class RiskQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public RiskQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Seed a risk_decision row. Useful for downstream tests that need a pre-existing decision.
   * Request fields: signalId (required), decision (ALLOW|DENY), traceId, agentId, policyVersion.
   */
  @PostMapping("/seed")
  public Map<String, String> seed(@RequestBody Map<String, Object> req) {
    String riskDecisionId = "qa-risk-" + UUID.randomUUID();
    String signalId       = str(req, "signalId", "qa-signal-" + UUID.randomUUID());
    String traceId        = str(req, "traceId",  "qa-trace-"  + UUID.randomUUID());
    String agentId        = str(req, "agentId",  "qa-agent");
    String decision       = str(req, "decision", "ALLOW");
    String policyVersion  = str(req, "policyVersion", "qa-policy-v1");
    Timestamp now         = Timestamp.from(Instant.now());

    jdbc.update(
        "INSERT INTO risk_decisions "
        + "(risk_decision_id, signal_id, trace_id, decision, deny_reasons_json, "
        + " policy_version, policy_rule_set, matched_rule_ids_json, failure_mode, created_at) "
        + "VALUES (:id, :signalId, :traceId, :decision, '[]', "
        + " :policyVersion, 'qa-rule-set', '[]', 'NONE', :now)",
        new MapSqlParameterSource()
            .addValue("id",            riskDecisionId)
            .addValue("signalId",      signalId)
            .addValue("traceId",       traceId)
            .addValue("decision",      decision)
            .addValue("policyVersion", policyVersion)
            .addValue("now",           now));

    // Also seed corresponding policy_decision_log
    String logId = "qa-log-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO policy_decision_log "
        + "(log_id, risk_decision_id, trace_id, agent_id, signal_id, "
        + " decision, policy_version, policy_rule_set, latency_ms, created_at) "
        + "VALUES (:logId, :riskDecisionId, :traceId, :agentId, :signalId, "
        + " :decision, :policyVersion, 'qa-rule-set', 0, :now)",
        new MapSqlParameterSource()
            .addValue("logId",          logId)
            .addValue("riskDecisionId", riskDecisionId)
            .addValue("traceId",        traceId)
            .addValue("agentId",        agentId)
            .addValue("signalId",       signalId)
            .addValue("decision",       decision)
            .addValue("policyVersion",  policyVersion)
            .addValue("now",            now));

    return Map.of(
        "risk_decision_id", riskDecisionId,
        "log_id",           logId,
        "signal_id",        signalId,
        "trace_id",         traceId,
        "decision",         decision);
  }

  @GetMapping("/state")
  public Map<String, Object> state(
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String signalId,
      @RequestParam(required = false) String agentId) {
    List<Map<String, Object>> decisions;
    List<Map<String, Object>> logs;
    if (traceId != null) {
      decisions = jdbc.queryForList(
          "SELECT * FROM risk_decisions WHERE trace_id = :v", Map.of("v", traceId));
      logs = jdbc.queryForList(
          "SELECT * FROM policy_decision_log WHERE trace_id = :v", Map.of("v", traceId));
    } else if (signalId != null) {
      decisions = jdbc.queryForList(
          "SELECT * FROM risk_decisions WHERE signal_id = :v", Map.of("v", signalId));
      logs = jdbc.queryForList(
          "SELECT * FROM policy_decision_log WHERE signal_id = :v", Map.of("v", signalId));
    } else if (agentId != null) {
      decisions = jdbc.queryForList(
          "SELECT * FROM risk_decisions rd JOIN policy_decision_log pdl "
          + "ON rd.risk_decision_id = pdl.risk_decision_id WHERE pdl.agent_id = :v",
          Map.of("v", agentId));
      logs = jdbc.queryForList(
          "SELECT * FROM policy_decision_log WHERE agent_id = :v", Map.of("v", agentId));
    } else {
      decisions = List.of();
      logs = List.of();
    }
    return Map.of("risk_decisions", decisions, "policy_decision_log", logs);
  }

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String testRunPrefix) {
    int deleted = 0;
    if (agentId != null) {
      deleted += jdbc.update(
          "DELETE FROM policy_decision_log WHERE agent_id = :v", Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM risk_decisions WHERE risk_decision_id IN "
          + "(SELECT risk_decision_id FROM policy_decision_log WHERE agent_id = :v)",
          Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM risk_events WHERE agent_id = :v", Map.of("v", agentId));
    }
    if (testRunPrefix != null) {
      deleted += jdbc.update(
          "DELETE FROM risk_decisions WHERE risk_decision_id LIKE :v",
          Map.of("v", testRunPrefix + "%"));
    }
    return Map.of("deleted_rows", deleted);
  }

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String traceId) {
    return Map.of(
        "trace_id",            traceId,
        "risk_decisions",      jdbc.queryForList(
            "SELECT * FROM risk_decisions WHERE trace_id = :v", Map.of("v", traceId)),
        "policy_decision_log", jdbc.queryForList(
            "SELECT * FROM policy_decision_log WHERE trace_id = :v", Map.of("v", traceId)));
  }

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }
}
