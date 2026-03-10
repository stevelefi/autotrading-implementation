package com.autotrading.libs.auth;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class BrokerAccountCacheTest {

  private static final String AGENT_ID    = "agent-local";
  private static final String EXT_ACCOUNT = "DU123456";
  private static final String DEFAULT_ACC = "DU999999";

  private EmbeddedDatabase db;
  private JdbcTemplate     jdbc;

  @BeforeEach
  void setUp() {
    db = new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .addScript("classpath:auth-test-schema.sql")
        .build();
    jdbc = new JdbcTemplate(db);
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.shutdown();
  }

  // ------------------------------------------------------------------
  // resolveExternalAccountId
  // ------------------------------------------------------------------

  @Test
  void resolvesKnownAgent() {
    seedDb();
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.refresh();
    assertThat(cache.resolveExternalAccountId(AGENT_ID)).hasValue(EXT_ACCOUNT);
  }

  @Test
  void returnsEmptyForUnknownAgent() {
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.refresh();
    assertThat(cache.resolveExternalAccountId("agent-unknown")).isEmpty();
  }

  @Test
  void returnsEmptyForNullOrBlankAgent() {
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.refresh();
    assertThat(cache.resolveExternalAccountId(null)).isEmpty();
    assertThat(cache.resolveExternalAccountId("")).isEmpty();
    assertThat(cache.resolveExternalAccountId("  ")).isEmpty();
  }

  // ------------------------------------------------------------------
  // resolveOrDefault
  // ------------------------------------------------------------------

  @Test
  void resolveOrDefaultReturnsMappedAccount() {
    seedDb();
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.refresh();
    assertThat(cache.resolveOrDefault(AGENT_ID)).isEqualTo(EXT_ACCOUNT);
  }

  @Test
  void resolveOrDefaultReturnsFallbackForUnknownAgent() {
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.refresh();
    assertThat(cache.resolveOrDefault("no-such-agent")).isEqualTo(DEFAULT_ACC);
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Test
  void phaseIs40() {
    assertThat(new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC).getPhase()).isEqualTo(40);
  }

  @Test
  void isNotRunningBeforeStart() {
    assertThat(new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC).isRunning()).isFalse();
  }

  @Test
  void isRunningAfterStart() throws InterruptedException {
    seedDb();
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.start();
    Thread.sleep(200);
    assertThat(cache.isRunning()).isTrue();
    cache.stop();
    assertThat(cache.isRunning()).isFalse();
  }

  // ------------------------------------------------------------------
  // Resilience — DB error keeps last known map
  // ------------------------------------------------------------------

  @Test
  void refreshKeepsLastKnownCacheOnDbError() {
    seedDb();
    BrokerAccountCache cache = new BrokerAccountCache(jdbc, 60_000, DEFAULT_ACC);
    cache.refresh();
    assertThat(cache.resolveOrDefault(AGENT_ID)).isEqualTo(EXT_ACCOUNT);

    // Simulate a DB error by dropping the table
    jdbc.execute("DROP TABLE broker_accounts");
    cache.refresh(); // should swallow the error and keep last known cache
    assertThat(cache.resolveOrDefault(AGENT_ID)).isEqualTo(EXT_ACCOUNT);
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private void seedDb() {
    jdbc.update("INSERT INTO accounts(account_id, display_name, active) VALUES(?, ?, TRUE)",
        "acc-local-dev", "Local Dev Account");
    jdbc.update("INSERT INTO agents(agent_id, account_id, display_name, active) VALUES(?, ?, ?, TRUE)",
        AGENT_ID, "acc-local-dev", "Local Dev Agent");
    jdbc.update(
        "INSERT INTO broker_accounts(broker_account_id, agent_id, broker_id, external_account_id, active) VALUES(?, ?, ?, ?, TRUE)",
        "ba-local-dev", AGENT_ID, "ibkr", EXT_ACCOUNT);
  }
}
