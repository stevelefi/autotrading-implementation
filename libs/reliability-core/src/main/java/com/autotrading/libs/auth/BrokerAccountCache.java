package com.autotrading.libs.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cache-backed resolver for agent → IBKR external account ID routing.
 *
 * <p>On the hot path ({@link #resolveExternalAccountId}) there is <em>no DB I/O</em>:
 * lookup is an O(1) {@link ConcurrentHashMap} read.
 *
 * <p>A background thread (name: {@code broker-account-cache}) polls the
 * {@code broker_accounts} table every {@code refreshIntervalMs} milliseconds.
 *
 * <h3>Failure policy</h3>
 * On DB error the last known map is retained. If the agent has no entry, the caller
 * may fall back to a default account configured via {@code ibkr.cp.account-id}.
 *
 * <h3>Spring lifecycle</h3>
 * Phase {@code 40} — same as {@link ApiKeyAuthenticator}, before {@code BrokerHealthCache}
 * (phase 50) and before gRPC/HTTP servers start.
 */
public class BrokerAccountCache implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(BrokerAccountCache.class);
  private static final int PHASE = 40;

  private final JdbcTemplate jdbc;
  private final long refreshIntervalMs;
  private final String fallbackAccountId;

  // agentId → externalAccountId (e.g. "DU123456")
  private volatile Map<String, String> cache = new ConcurrentHashMap<>();

  private ScheduledExecutorService scheduler;

  /**
   * @param jdbc               JDBC template (shared with the service's DataSource)
   * @param refreshIntervalMs  polling interval in milliseconds
   * @param fallbackAccountId  IBKR account ID to use when an agent has no mapped broker
   *                           account (matches legacy {@code ibkr.cp.account-id} config)
   */
  public BrokerAccountCache(JdbcTemplate jdbc, long refreshIntervalMs, String fallbackAccountId) {
    this.jdbc              = jdbc;
    this.refreshIntervalMs = refreshIntervalMs;
    this.fallbackAccountId = fallbackAccountId;
  }

  // ------------------------------------------------------------------
  // Public API (hot path — no I/O)
  // ------------------------------------------------------------------

  /**
   * Resolves the IBKR external account ID for the given {@code agentId}.
   *
   * <p>Returns the cached mapping if present; otherwise returns
   * {@link Optional#empty()} — callers should fall back to the configured default.
   */
  public Optional<String> resolveExternalAccountId(String agentId) {
    if (agentId == null || agentId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(cache.get(agentId));
  }

  /**
   * Convenience method returning the resolved account ID or the configured fallback.
   */
  public String resolveOrDefault(String agentId) {
    return resolveExternalAccountId(agentId).orElse(fallbackAccountId);
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "broker-account-cache");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleAtFixedRate(this::refresh, 0, refreshIntervalMs, TimeUnit.MILLISECONDS);
    log.info("BrokerAccountCache started refreshIntervalMs={} fallback={}", refreshIntervalMs, fallbackAccountId);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    log.info("BrokerAccountCache stopped");
  }

  @Override public boolean isRunning()     { return scheduler != null && !scheduler.isShutdown(); }
  @Override public boolean isAutoStartup() { return true; }
  @Override public int     getPhase()      { return PHASE; }

  // ------------------------------------------------------------------
  // Internal
  // ------------------------------------------------------------------

  void refresh() {
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT agent_id, external_account_id FROM broker_accounts WHERE active = TRUE");

      Map<String, String> newCache = new ConcurrentHashMap<>();
      for (Map<String, Object> row : rows) {
        newCache.put((String) row.get("agent_id"), (String) row.get("external_account_id"));
      }
      cache = newCache;
      log.debug("BrokerAccountCache refreshed entries={}", newCache.size());
    } catch (Exception e) {
      log.warn("BrokerAccountCache refresh failed — keeping last known cache: {}", e.getMessage());
    }
  }
}
