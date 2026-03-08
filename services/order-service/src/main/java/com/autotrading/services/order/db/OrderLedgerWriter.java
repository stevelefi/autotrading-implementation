package com.autotrading.services.order.db;

import java.time.Instant;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OCC-safe writer for order_ledger state transitions.
 *
 * <p>Uses a conditional UPDATE that checks state_version to prevent concurrent
 * overwrites. The caller must supply the expected current version; if the row
 * has already been updated by another thread/node, zero rows are affected and
 * {@link OptimisticLockingFailureException} is thrown so the caller can retry.
 *
 * <p>This replaces the Hibernate dirty-check approach and makes the concurrency
 * contract explicit at the SQL level.
 */
@Component
public class OrderLedgerWriter {

  private static final String TRANSITION_SQL =
      "UPDATE order_ledger "
      + "SET state          = :newState, "
      + "    state_version  = state_version + 1, "
      + "    last_status_at = :lastStatusAt, "
      + "    updated_at     = :updatedAt "
      + "WHERE order_intent_id = :orderIntentId "
      + "  AND state_version   = :expectedVersion";

  private final NamedParameterJdbcTemplate jdbc;

  public OrderLedgerWriter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Atomically transitions an order to {@code newState} if and only if
   * {@code expectedVersion} matches the current {@code state_version}.
   *
   * @param orderIntentId   the order to transition
   * @param newState        target state
   * @param expectedVersion the state_version the caller read before calling this
   * @param lastStatusAt    timestamp of the status event (may be null)
   * @param updatedAt       write timestamp
   * @throws OptimisticLockingFailureException if the row was concurrently modified
   */
  public void transition(String orderIntentId, String newState,
                         long expectedVersion, Instant lastStatusAt, Instant updatedAt) {
    int rows = jdbc.update(TRANSITION_SQL, new MapSqlParameterSource()
        .addValue("orderIntentId",   orderIntentId)
        .addValue("newState",        newState)
        .addValue("expectedVersion", expectedVersion)
        .addValue("lastStatusAt",    lastStatusAt == null ? null
                                     : java.sql.Timestamp.from(lastStatusAt))
        .addValue("updatedAt",       java.sql.Timestamp.from(updatedAt)));
    if (rows == 0) {
      throw new OptimisticLockingFailureException(
          "Order ledger concurrent modification detected for order_intent_id=" + orderIntentId
          + " expected state_version=" + expectedVersion);
    }
  }
}
