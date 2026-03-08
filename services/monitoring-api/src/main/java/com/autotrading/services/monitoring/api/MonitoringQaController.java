package com.autotrading.services.monitoring.api;

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
 * Internal QA / automation API for monitoring-api.
 * Only active when {@code qa.api.enabled=true} (never in production).
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class MonitoringQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public MonitoringQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ─── Seed ────────────────────────────────────────────────────────────────────

  /**
   * Seed a system_control row and optionally a reconciliation_run row.
   * Request fields: controlKey, controlValue, actorId, traceId,
   *                 withReconciliation (boolean), reconciliationStatus.
   * Returns: controlKey, runId (if seeded).
   */
  @PostMapping("/seed")
  public Map<String, Object> seed(@RequestBody Map<String, Object> req) {
    String controlKey   = str(req, "controlKey",   "trading_mode");
    String controlValue = str(req, "controlValue", "ACTIVE");
    String actorId      = str(req, "actorId",      "qa-actor");
    String traceId      = str(req, "traceId",      "qa-trace-" + UUID.randomUUID());
    Timestamp now = Timestamp.from(Instant.now());

    jdbc.update(
        "INSERT INTO system_controls (control_key, control_value, actor_id, trace_id, updated_at) "
        + "VALUES (:controlKey, :controlValue, :actorId, :traceId, :now) "
        + "ON CONFLICT (control_key) DO UPDATE SET "
        + "  control_value = EXCLUDED.control_value, "
        + "  actor_id      = EXCLUDED.actor_id, "
        + "  trace_id      = EXCLUDED.trace_id, "
        + "  updated_at    = EXCLUDED.updated_at",
        new MapSqlParameterSource()
            .addValue("controlKey",   controlKey)
            .addValue("controlValue", controlValue)
            .addValue("actorId",      actorId)
            .addValue("traceId",      traceId)
            .addValue("now",          now));

    boolean withReconciliation = Boolean.parseBoolean(str(req, "withReconciliation", "false"));
    String runId = null;
    if (withReconciliation) {
      runId = "qa-recon-" + UUID.randomUUID();
      String status = str(req, "reconciliationStatus", "COMPLETED");
      jdbc.update(
          "INSERT INTO reconciliation_runs "
          + "(run_id, status, mismatch_summary_json, started_at, ended_at) "
          + "VALUES (:runId, :status, NULL, :now, :now)",
          new MapSqlParameterSource()
              .addValue("runId",  runId)
              .addValue("status", status)
              .addValue("now",    now));
    }

    return runId != null
        ? Map.of("control_key", controlKey, "run_id", runId)
        : Map.of("control_key", controlKey);
  }

  // ─── State query ─────────────────────────────────────────────────────────────

  @GetMapping("/state")
  public Map<String, Object> state(
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String controlKey,
      @RequestParam(required = false) String runStatus) {
    if (controlKey != null) {
      return Map.of("system_controls", jdbc.queryForList(
          "SELECT * FROM system_controls WHERE control_key = :v", Map.of("v", controlKey)));
    }
    if (traceId != null) {
      return Map.of("system_controls", jdbc.queryForList(
          "SELECT * FROM system_controls WHERE trace_id = :v", Map.of("v", traceId)));
    }
    if (runStatus != null) {
      return Map.of("reconciliation_runs", jdbc.queryForList(
          "SELECT * FROM reconciliation_runs WHERE status = :v ORDER BY started_at DESC LIMIT 20",
          Map.of("v", runStatus)));
    }
    return Map.of(
        "system_controls", jdbc.queryForList(
            "SELECT * FROM system_controls ORDER BY updated_at DESC",
            Map.<String, Object>of()),
        "reconciliation_runs_recent", jdbc.queryForList(
            "SELECT * FROM reconciliation_runs ORDER BY started_at DESC LIMIT 10",
            Map.<String, Object>of()));
  }

  // ─── Teardown ────────────────────────────────────────────────────────────────

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String controlKeyPrefix,
      @RequestParam(required = false) String traceId) {
    int deleted = 0;
    if (actorId != null) {
      deleted += jdbc.update(
          "DELETE FROM system_controls WHERE actor_id = :v", Map.of("v", actorId));
    }
    if (controlKeyPrefix != null) {
      deleted += jdbc.update(
          "DELETE FROM system_controls WHERE control_key LIKE :v",
          Map.of("v", controlKeyPrefix + "%"));
    }
    if (traceId != null) {
      deleted += jdbc.update(
          "DELETE FROM system_controls WHERE trace_id = :v", Map.of("v", traceId));
      // clean up any QA reconciliation runs seeded in the same test by run_id prefix
      deleted += jdbc.update(
          "DELETE FROM reconciliation_runs WHERE run_id LIKE 'qa-%'",
          Map.<String, Object>of());
    }
    return Map.of("deleted_rows", deleted);
  }

  // ─── Snapshot ────────────────────────────────────────────────────────────────

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam(required = false) String traceId) {
    return Map.of(
        "system_controls", traceId != null
            ? jdbc.queryForList(
                "SELECT * FROM system_controls WHERE trace_id = :v", Map.of("v", traceId))
            : jdbc.queryForList(
                "SELECT * FROM system_controls ORDER BY updated_at DESC",
                Map.<String, Object>of()),
        "reconciliation_runs", jdbc.queryForList(
            "SELECT * FROM reconciliation_runs ORDER BY started_at DESC LIMIT 20",
            Map.<String, Object>of()));
  }

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    if (m == null) return defaultVal;
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }
}
