package com.autotrading.libs.health;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Periodically polls {@code broker_health_status} (written by ibkr-connector-service on every
 * UP/DOWN transition) and caches the result locally.
 *
 * <p>Consumers call {@link #isBrokerAvailable()} on the hot path — no DB round-trip.
 *
 * <h3>Failure policy</h3>
 * <ul>
 *   <li>Row not yet present (e.g. Flyway not yet applied) → treated as <em>available</em>.
 *   <li>DB query throws → keeps the last known value (optimistic — a DB blip must not
 *       stop all trading).
 *   <li>Status = {@code UNKNOWN} (startup) → treated as <em>available</em>.
 * </ul>
 *
 * <p>Declare as a Spring bean in the service's {@code @Configuration} class (phase {@code 50}
 * means the cache is ready before the embedded web server or gRPC server starts accepting
 * connections).
 */
public class BrokerHealthCache implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(BrokerHealthCache.class);
  private static final int PHASE = 50;
  private static final String BROKER_ID = "ibkr";

  private final JdbcTemplate jdbc;
  private final long refreshIntervalMs;

  /**
   * {@code true} by default — optimistic until the first successful DB poll.
   * Protects against a race where the cache starts but the first query hasn't
   * completed yet and a request arrives.
   */
  private volatile boolean brokerAvailable = true;
  private volatile Instant lastRefreshedAt = Instant.EPOCH;
  private ScheduledExecutorService scheduler;

  public BrokerHealthCache(JdbcTemplate jdbc, long refreshIntervalMs) {
    this.jdbc = jdbc;
    this.refreshIntervalMs = refreshIntervalMs;
  }

  // ------------------------------------------------------------------
  // Public API (hot path — no I/O)
  // ------------------------------------------------------------------

  /** Returns {@code true} when the broker is UP or UNKNOWN; {@code false} only for DOWN. */
  public boolean isBrokerAvailable() {
    return brokerAvailable;
  }

  /** Timestamp of the last successful DB poll (or {@link Instant#EPOCH} if never polled). */
  public Instant lastRefreshedAt() {
    return lastRefreshedAt;
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "broker-health-cache");
      t.setDaemon(true);
      return t;
    });
    // Delay=0 so the first poll happens immediately at startup
    scheduler.scheduleAtFixedRate(this::refresh, 0, refreshIntervalMs, TimeUnit.MILLISECONDS);
    log.info("BrokerHealthCache started refreshIntervalMs={}", refreshIntervalMs);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    log.info("BrokerHealthCache stopped");
  }

  @Override public boolean isRunning()     { return scheduler != null && !scheduler.isShutdown(); }
  @Override public boolean isAutoStartup() { return true; }
  @Override public int     getPhase()      { return PHASE; }

  // ------------------------------------------------------------------
  // Internal
  // ------------------------------------------------------------------

  void refresh() {
    try {
      List<String> rows = jdbc.queryForList(
          "SELECT status FROM broker_health_status WHERE broker_id = ?",
          String.class, BROKER_ID);

      if (rows.isEmpty()) {
        // Table exists but no row yet — keep optimistic default
        return;
      }

      boolean available = !"DOWN".equals(rows.get(0));
      if (brokerAvailable != available) {
        log.info("BrokerHealthCache transition {} → {}",
            brokerAvailable ? "AVAILABLE" : "DOWN",
            available      ? "AVAILABLE" : "DOWN");
      }
      brokerAvailable = available;
      lastRefreshedAt = Instant.now();

    } catch (Exception e) {
      // Optimistic: do NOT flip to unavailable on a transient DB error.
      log.warn("BrokerHealthCache DB refresh failed — keeping brokerAvailable={} cause={}",
          brokerAvailable, e.getMessage());
    }
  }
}
