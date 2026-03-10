package com.autotrading.libs.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cache-backed API key authenticator.
 *
 * <p>On every hot-path call ({@link #authenticate}) there is <em>no DB I/O</em>: lookup is
 * an O(1) {@link ConcurrentHashMap} read on a pre-computed SHA-256 digest.
 *
 * <p>A background thread (name: {@code api-key-cache}) polls the database every
 * {@code refreshIntervalMs} milliseconds and atomically swaps the in-memory maps.
 *
 * <h3>Failure policy</h3>
 * <ul>
 *   <li>DB error during refresh → keep the last known maps (same policy as
 *       {@code BrokerHealthCache} — a DB blip must not lock out all trading).
 *   <li>Empty DB (no active keys) → map is cleared; all requests will receive 401.
 * </ul>
 *
 * <h3>Spring lifecycle</h3>
 * Declare as a Spring bean at {@code SmartLifecycle} phase {@code 40} (before
 * {@code BrokerHealthCache} at phase {@code 50}) so the cache is warm before any other
 * infrastructure component starts accepting connections.
 */
public class ApiKeyAuthenticator implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticator.class);
  private static final int PHASE = 40;

  /** Refresh SQL — loads all active, non-expired keys with their agent ownership data. */
  private static final String REFRESH_SQL =
      "SELECT k.key_hash, k.account_id, k.generation, a.agent_id " +
      "FROM account_api_keys k " +
      "JOIN accounts acc ON acc.account_id = k.account_id " +
      "LEFT JOIN agents a ON a.account_id = k.account_id AND a.active = TRUE " +
      "WHERE k.active = TRUE " +
      "  AND acc.active = TRUE " +
      "  AND (k.expires_at IS NULL OR k.expires_at > now())";

  private final JdbcTemplate jdbc;
  private final long refreshIntervalMs;

  // key_hash → principal (hot path, no I/O)
  private volatile Map<String, AuthenticatedPrincipal> keyCache = new ConcurrentHashMap<>();
  // account_id → set of agent_ids (ownership check)
  private volatile Map<String, Set<String>> agentsByAccount = new ConcurrentHashMap<>();

  private ScheduledExecutorService scheduler;

  public ApiKeyAuthenticator(JdbcTemplate jdbc, long refreshIntervalMs) {
    this.jdbc = jdbc;
    this.refreshIntervalMs = refreshIntervalMs;
  }

  // ------------------------------------------------------------------
  // Public API (hot path — no I/O)
  // ------------------------------------------------------------------

  /**
   * Authenticates a raw Bearer token.
   *
   * <p>Computes SHA-256 of the raw key and looks it up in the in-memory cache.
   *
   * @param rawKey the raw token string (without {@code "Bearer "} prefix)
   * @return {@link Optional#empty()} when the key is unknown/inactive/expired;
   *         otherwise the resolved {@link AuthenticatedPrincipal}
   */
  public Optional<AuthenticatedPrincipal> authenticate(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      return Optional.empty();
    }
    String hash = sha256Hex(rawKey);
    return Optional.ofNullable(keyCache.get(hash));
  }

  /**
   * Returns {@code true} when the given {@code agentId} belongs to the given
   * {@code accountId} according to the agents table snapshot.
   */
  public boolean isAgentOwnedBy(String agentId, String accountId) {
    if (agentId == null || accountId == null) return false;
    Set<String> agents = agentsByAccount.get(accountId);
    return agents != null && agents.contains(agentId);
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "api-key-cache");
      t.setDaemon(true);
      return t;
    });
    // Delay=0 — first poll executes immediately so cache is populated before any request
    scheduler.scheduleAtFixedRate(this::refresh, 0, refreshIntervalMs, TimeUnit.MILLISECONDS);
    log.info("ApiKeyAuthenticator started refreshIntervalMs={}", refreshIntervalMs);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    log.info("ApiKeyAuthenticator stopped");
  }

  @Override public boolean isRunning()     { return scheduler != null && !scheduler.isShutdown(); }
  @Override public boolean isAutoStartup() { return true; }
  @Override public int     getPhase()      { return PHASE; }

  // ------------------------------------------------------------------
  // Internal
  // ------------------------------------------------------------------

  public void refresh() {
    try {
      Map<String, AuthenticatedPrincipal> newKeyCache = new ConcurrentHashMap<>();
      Map<String, Set<String>> newAgentsByAccount = new ConcurrentHashMap<>();

      List<Map<String, Object>> rows = jdbc.queryForList(REFRESH_SQL);
      for (Map<String, Object> row : rows) {
        String keyHash   = (String) row.get("key_hash");
        String accountId = (String) row.get("account_id");
        int    generation = ((Number) row.get("generation")).intValue();
        String agentId   = (String) row.get("agent_id");

        newKeyCache.put(keyHash, new AuthenticatedPrincipal(accountId, keyHash, generation));

        if (agentId != null) {
          newAgentsByAccount
              .computeIfAbsent(accountId, k -> new HashSet<>())
              .add(agentId);
        }
      }

      keyCache        = newKeyCache;
      agentsByAccount = newAgentsByAccount;
      log.debug("ApiKeyAuthenticator refreshed keys={} accounts={}", newKeyCache.size(), newAgentsByAccount.size());
    } catch (Exception e) {
      // Keep last known maps — DB blip must not lock out trading
      log.warn("ApiKeyAuthenticator refresh failed — keeping last known cache: {}", e.getMessage());
    }
  }

  // ------------------------------------------------------------------
  // Utilities
  // ------------------------------------------------------------------

  public static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
