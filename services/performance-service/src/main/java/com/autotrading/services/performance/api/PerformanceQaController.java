package com.autotrading.services.performance.api;

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
 * Internal QA / automation API for performance-service.
 * Only active when {@code qa.api.enabled=true} (never in production).
 */
@RestController
@RequestMapping("/internal/qa")
@ConditionalOnProperty(name = "qa.api.enabled", havingValue = "true")
public class PerformanceQaController {

  private final NamedParameterJdbcTemplate jdbc;

  public PerformanceQaController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ─── Seed ────────────────────────────────────────────────────────────────────

  /**
   * Seed a position (upsert) and optionally a pnl_snapshot row.
   * Request fields: agentId, instrumentId, qty, avgCost, realizedPnl,
   *                 withSnapshot (boolean), unrealizedPnl, totalPnl.
   * Returns: agentId, instrumentId, snapshotId (if seeded).
   */
  @PostMapping("/seed")
  public Map<String, Object> seed(@RequestBody Map<String, Object> req) {
    String agentId      = str(req, "agentId",      "qa-agent");
    String instrumentId = str(req, "instrumentId", "QA_INSTRUMENT");
    BigDecimal qty         = dec(req, "qty",         "0");
    BigDecimal avgCost     = dec(req, "avgCost",     "100.00");
    BigDecimal realizedPnl = dec(req, "realizedPnl", "0.00");
    Timestamp now = Timestamp.from(Instant.now());

    jdbc.update(
        "INSERT INTO positions (agent_id, instrument_id, qty, avg_cost, realized_pnl, updated_at) "
        + "VALUES (:agentId, :instrumentId, :qty, :avgCost, :realizedPnl, :now) "
        + "ON CONFLICT (agent_id, instrument_id) DO UPDATE SET "
        + "  qty          = EXCLUDED.qty, "
        + "  avg_cost     = EXCLUDED.avg_cost, "
        + "  realized_pnl = EXCLUDED.realized_pnl, "
        + "  updated_at   = EXCLUDED.updated_at",
        new MapSqlParameterSource()
            .addValue("agentId",      agentId)
            .addValue("instrumentId", instrumentId)
            .addValue("qty",          qty)
            .addValue("avgCost",      avgCost)
            .addValue("realizedPnl",  realizedPnl)
            .addValue("now",          now));

    boolean withSnapshot = Boolean.parseBoolean(str(req, "withSnapshot", "false"));
    String snapshotId = null;
    if (withSnapshot) {
      snapshotId = "qa-pnl-" + UUID.randomUUID();
      BigDecimal unrealizedPnl = dec(req, "unrealizedPnl", "0.00");
      BigDecimal totalPnl      = dec(req, "totalPnl",      "0.00");
      jdbc.update(
          "INSERT INTO pnl_snapshots "
          + "(snapshot_id, agent_id, instrument_id, realized_pnl, unrealized_pnl, "
          + " total_pnl, snapshot_ts) "
          + "VALUES (:id, :agentId, :instrumentId, :realizedPnl, :unrealizedPnl, "
          + "        :totalPnl, :now)",
          new MapSqlParameterSource()
              .addValue("id",           snapshotId)
              .addValue("agentId",      agentId)
              .addValue("instrumentId", instrumentId)
              .addValue("realizedPnl",  realizedPnl)
              .addValue("unrealizedPnl", unrealizedPnl)
              .addValue("totalPnl",     totalPnl)
              .addValue("now",          now));
    }

    return snapshotId != null
        ? Map.of("agent_id", agentId, "instrument_id", instrumentId, "snapshot_id", snapshotId)
        : Map.of("agent_id", agentId, "instrument_id", instrumentId);
  }

  // ─── State query ─────────────────────────────────────────────────────────────

  @GetMapping("/state")
  public Map<String, Object> state(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String instrumentId) {
    if (agentId != null && instrumentId != null) {
      return Map.of(
          "position", jdbc.queryForList(
              "SELECT * FROM positions WHERE agent_id = :a AND instrument_id = :i",
              Map.of("a", agentId, "i", instrumentId)),
          "pnl_snapshots", jdbc.queryForList(
              "SELECT * FROM pnl_snapshots WHERE agent_id = :a AND instrument_id = :i "
              + "ORDER BY snapshot_ts DESC LIMIT 20",
              Map.of("a", agentId, "i", instrumentId)));
    }
    if (agentId != null) {
      return Map.of(
          "positions", jdbc.queryForList(
              "SELECT * FROM positions WHERE agent_id = :v", Map.of("v", agentId)),
          "pnl_snapshots", jdbc.queryForList(
              "SELECT * FROM pnl_snapshots WHERE agent_id = :v ORDER BY snapshot_ts DESC LIMIT 50",
              Map.of("v", agentId)));
    }
    return Map.of();
  }

  // ─── Teardown ────────────────────────────────────────────────────────────────

  @DeleteMapping("/data")
  public Map<String, Integer> teardown(
      @RequestParam(required = false) String agentId,
      @RequestParam(required = false) String instrumentId) {
    int deleted = 0;
    if (agentId != null) {
      deleted += jdbc.update(
          "DELETE FROM pnl_snapshots WHERE agent_id = :v", Map.of("v", agentId));
      deleted += jdbc.update(
          "DELETE FROM positions WHERE agent_id = :v", Map.of("v", agentId));
    }
    if (instrumentId != null) {
      deleted += jdbc.update(
          "DELETE FROM pnl_snapshots WHERE instrument_id = :v", Map.of("v", instrumentId));
      deleted += jdbc.update(
          "DELETE FROM positions WHERE instrument_id = :v", Map.of("v", instrumentId));
    }
    return Map.of("deleted_rows", deleted);
  }

  // ─── Snapshot ────────────────────────────────────────────────────────────────

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(@RequestParam String agentId) {
    return Map.of(
        "agent_id",      agentId,
        "positions",     jdbc.queryForList(
            "SELECT * FROM positions WHERE agent_id = :v", Map.of("v", agentId)),
        "pnl_snapshots", jdbc.queryForList(
            "SELECT * FROM pnl_snapshots WHERE agent_id = :v ORDER BY snapshot_ts DESC",
            Map.of("v", agentId)));
  }

  private static String str(Map<String, Object> m, String key, String defaultVal) {
    if (m == null) return defaultVal;
    Object v = m.get(key);
    return v == null ? defaultVal : v.toString();
  }

  private static BigDecimal dec(Map<String, Object> m, String key, String defaultVal) {
    return new BigDecimal(str(m, key, defaultVal));
  }
}
