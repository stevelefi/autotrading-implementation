package com.autotrading.services.ibkr.health;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.autotrading.services.ibkr.client.BrokerStatus;

/**
 * Persists {@link BrokerStatus} transitions to the shared {@code broker_health_status} table.
 *
 * <p>Invoked by {@link com.autotrading.services.ibkr.client.IbkrHealthProbe} only on
 * status <em>transitions</em> (UNKNOWN→UP, UP→DOWN, DOWN→UP) to avoid write amplification
 * on every 30-second tickle.
 *
 * <p>DB failures are logged and swallowed — a persist failure must not stop broker operations.
 * The worst outcome is that readers (ingress, order-service) keep a stale cached value for
 * one extra refresh cycle (≤ 15 s).
 */
public class BrokerHealthPersister {

  private static final Logger log = LoggerFactory.getLogger(BrokerHealthPersister.class);
  private static final String BROKER_ID = "ibkr";

  private final NamedParameterJdbcTemplate jdbc;

  public BrokerHealthPersister(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Upserts the broker health row.  Called only when the status has changed.
   *
   * @param newStatus the new {@link BrokerStatus} after the transition
   */
  public void onTransition(BrokerStatus newStatus) {
    try {
      jdbc.update(
          """
          INSERT INTO broker_health_status (broker_id, status, updated_at)
          VALUES (:brokerId, :status, :updatedAt)
          ON CONFLICT (broker_id) DO UPDATE
              SET status     = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at
          """,
          new MapSqlParameterSource()
              .addValue("brokerId", BROKER_ID)
              .addValue("status", newStatus.name())
              .addValue("updatedAt", Timestamp.from(Instant.now())));

      log.info("broker health persisted brokerId={} status={}", BROKER_ID, newStatus);
    } catch (Exception e) {
      log.warn("broker health persist failed status={} cause={}", newStatus, e.getMessage(), e);
    }
  }
}
