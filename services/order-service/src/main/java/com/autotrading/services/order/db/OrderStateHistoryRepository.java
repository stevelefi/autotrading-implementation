package com.autotrading.services.order.db;

import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for order_state_history (composite PK: order_intent_id + sequence_no).
 * sequence_no is assigned via MAX(sequence_no)+1 inside the INSERT to avoid a separate
 * SELECT round-trip while remaining safe under row-level locking.
 */
@Repository
public class OrderStateHistoryRepository {

  private static final RowMapper<OrderStateHistoryEntity> MAPPER = (rs, n) ->
      new OrderStateHistoryEntity(
          rs.getString("order_intent_id"),
          rs.getLong("sequence_no"),
          rs.getString("from_state"),
          rs.getString("to_state"),
          rs.getString("reason"),
          rs.getString("trace_id"),
          rs.getTimestamp("occurred_at").toInstant());

  private static final String INSERT_SQL =
      "INSERT INTO order_state_history "
      + "(order_intent_id, sequence_no, from_state, to_state, reason, trace_id, occurred_at) "
      + "VALUES (:orderIntentId, "
      + "  COALESCE((SELECT MAX(sequence_no)+1 FROM order_state_history "
      + "           WHERE order_intent_id = :orderIntentId), 1), "
      + "  :fromState, :toState, :reason, :traceId, :occurredAt)";

  private static final String FIND_BY_ORDER_SQL =
      "SELECT * FROM order_state_history WHERE order_intent_id = :orderIntentId "
      + "ORDER BY sequence_no ASC";

  private final NamedParameterJdbcTemplate jdbc;

  public OrderStateHistoryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Append a new state-transition row; sequence_no is auto-assigned. */
  public void save(OrderStateHistoryEntity entity) {
    jdbc.update(INSERT_SQL, new MapSqlParameterSource()
        .addValue("orderIntentId", entity.getOrderIntentId())
        .addValue("fromState",     entity.getFromState())
        .addValue("toState",       entity.getToState())
        .addValue("reason",        entity.getReason())
        .addValue("traceId",       entity.getTraceId())
        .addValue("occurredAt",    java.sql.Timestamp.from(entity.getOccurredAt())));
  }

  /** Returns all transition rows for an order, ordered by sequence_no ASC. */
  public List<OrderStateHistoryEntity> findByOrderIntentId(String orderIntentId) {
    return jdbc.query(FIND_BY_ORDER_SQL,
        new MapSqlParameterSource("orderIntentId", orderIntentId), MAPPER);
  }

  /**
   * Backward-compatible alias used by legacy call sites that referenced the
   * old JPA derived-query name based on @EmbeddedId navigation.
   */
  public List<OrderStateHistoryEntity> findByIdOrderIntentIdOrderByIdSequenceNoAsc(
      String orderIntentId) {
    return findByOrderIntentId(orderIntentId);
  }
}
