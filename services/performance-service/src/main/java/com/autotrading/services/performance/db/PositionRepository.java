package com.autotrading.services.performance.db;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for positions (composite PK: agent_id + instrument_id).
 * Writes are upserts via INSERT … ON CONFLICT DO UPDATE.
 */
@Repository
public class PositionRepository {

  private static final RowMapper<PositionEntity> MAPPER = (rs, n) ->
      new PositionEntity(
          rs.getString("agent_id"),
          rs.getString("instrument_id"),
          rs.getInt("qty"),
          rs.getBigDecimal("avg_cost"),
          rs.getBigDecimal("realized_pnl"),
          rs.getTimestamp("updated_at").toInstant());

  private static final String UPSERT_SQL =
      "INSERT INTO positions (agent_id, instrument_id, qty, avg_cost, realized_pnl, updated_at) "
      + "VALUES (:agentId, :instrumentId, :qty, :avgCost, :realizedPnl, :updatedAt) "
      + "ON CONFLICT (agent_id, instrument_id) DO UPDATE "
      + "  SET qty          = EXCLUDED.qty, "
      + "      avg_cost     = EXCLUDED.avg_cost, "
      + "      realized_pnl = EXCLUDED.realized_pnl, "
      + "      updated_at   = EXCLUDED.updated_at";

  private static final String FIND_BY_AGENT_SQL =
      "SELECT * FROM positions WHERE agent_id = :agentId";

  private static final String FIND_BY_AGENT_AND_INSTRUMENT_SQL =
      "SELECT * FROM positions WHERE agent_id = :agentId AND instrument_id = :instrumentId";

  private final NamedParameterJdbcTemplate jdbc;

  public PositionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Upsert a position row (insert or update on composite PK conflict). */
  public void save(PositionEntity entity) {
    jdbc.update(UPSERT_SQL, new MapSqlParameterSource()
        .addValue("agentId",      entity.getAgentId())
        .addValue("instrumentId", entity.getInstrumentId())
        .addValue("qty",          entity.getQty())
        .addValue("avgCost",      entity.getAvgCost())
        .addValue("realizedPnl",  entity.getRealizedPnl())
        .addValue("updatedAt",    java.sql.Timestamp.from(entity.getUpdatedAt())));
  }

  /** All positions for an agent. */
  public List<PositionEntity> findByAgentId(String agentId) {
    return jdbc.query(FIND_BY_AGENT_SQL,
        new MapSqlParameterSource("agentId", agentId), MAPPER);
  }

  /** Backward-compatible alias for old JPA embedded-ID derived query name. */
  public List<PositionEntity> findByIdAgentId(String agentId) {
    return findByAgentId(agentId);
  }

  /** Specific position by agent + instrument. */
  public Optional<PositionEntity> findByAgentIdAndInstrumentId(
      String agentId, String instrumentId) {
    List<PositionEntity> rows = jdbc.query(FIND_BY_AGENT_AND_INSTRUMENT_SQL,
        new MapSqlParameterSource()
            .addValue("agentId", agentId)
            .addValue("instrumentId", instrumentId),
        MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** Backward-compatible alias for old JPA embedded-ID derived query name. */
  public Optional<PositionEntity> findByIdAgentIdAndIdInstrumentId(
      String agentId, String instrumentId) {
    return findByAgentIdAndInstrumentId(agentId, instrumentId);
  }
}
