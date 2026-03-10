package com.autotrading.libs.auth;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class ApiKeyAuthenticatorTest {

  private static final String DEV_RAW_KEY     = "dev-api-key-do-not-use-in-production";
  private static final String DEV_KEY_HASH    = ApiKeyAuthenticator.sha256Hex(DEV_RAW_KEY);
  private static final String ACCOUNT_ID      = "acc-local-dev";
  private static final String AGENT_ID        = "agent-local";

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
  // sha256Hex utility
  // ------------------------------------------------------------------

  @Test
  void sha256HexProducesKnownDigest() {
    assertThat(DEV_KEY_HASH)
        .isEqualTo("3370ff290c7818d3886e91c10f85b48ba2562b3496a0237db94f1128e3531631");
  }

  // ------------------------------------------------------------------
  // authenticate
  // ------------------------------------------------------------------

  @Test
  void authenticateReturnsEmptyWhenCacheNotYetPopulated() {
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.authenticate(DEV_RAW_KEY)).isEmpty();
  }

  @Test
  void authenticateReturnsEmptyForNullOrBlankKey() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.authenticate(null)).isEmpty();
    assertThat(auth.authenticate("")).isEmpty();
    assertThat(auth.authenticate("  ")).isEmpty();
  }

  @Test
  void authenticateResolvesKnownKey() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();

    Optional<AuthenticatedPrincipal> result = auth.authenticate(DEV_RAW_KEY);

    assertThat(result).isPresent();
    assertThat(result.get().accountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.get().keyHash()).isEqualTo(DEV_KEY_HASH);
    assertThat(result.get().generation()).isEqualTo(1);
  }

  @Test
  void authenticateRejectsUnknownKey() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.authenticate("some-other-key")).isEmpty();
  }

  @Test
  void authenticateRejectsInactiveKey() {
    seedDb();
    jdbc.update("UPDATE account_api_keys SET active = FALSE WHERE key_hash = ?", DEV_KEY_HASH);
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.authenticate(DEV_RAW_KEY)).isEmpty();
  }

  @Test
  void authenticateRejectsKeyForInactiveAccount() {
    seedDb();
    jdbc.update("UPDATE accounts SET active = FALSE WHERE account_id = ?", ACCOUNT_ID);
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.authenticate(DEV_RAW_KEY)).isEmpty();
  }

  // ------------------------------------------------------------------
  // isAgentOwnedBy
  // ------------------------------------------------------------------

  @Test
  void isAgentOwnedByReturnsTrueForOwnedAgent() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.isAgentOwnedBy(AGENT_ID, ACCOUNT_ID)).isTrue();
  }

  @Test
  void isAgentOwnedByReturnsFalseForWrongAccount() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.isAgentOwnedBy(AGENT_ID, "acc-other")).isFalse();
  }

  @Test
  void isAgentOwnedByReturnsFalseForNullInputs() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
    assertThat(auth.isAgentOwnedBy(null, ACCOUNT_ID)).isFalse();
    assertThat(auth.isAgentOwnedBy(AGENT_ID, null)).isFalse();
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Test
  void phaseIs40() {
    assertThat(new ApiKeyAuthenticator(jdbc, 60_000).getPhase()).isEqualTo(40);
  }

  @Test
  void isNotRunningBeforeStart() {
    assertThat(new ApiKeyAuthenticator(jdbc, 60_000).isRunning()).isFalse();
  }

  @Test
  void isRunningAfterStart() throws InterruptedException {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.start();
    Thread.sleep(200); // let initial refresh complete
    assertThat(auth.isRunning()).isTrue();
    auth.stop();
    assertThat(auth.isRunning()).isFalse();
  }

  // ------------------------------------------------------------------
  // Resilience — DB error keeps last known map
  // ------------------------------------------------------------------

  @Test
  void refreshKeepsLastKnownCacheOnDbError() {
    seedDb();
    ApiKeyAuthenticator auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh(); // populates cache
    assertThat(auth.authenticate(DEV_RAW_KEY)).isPresent();

    // Simulate a DB error by dropping the table
    jdbc.execute("DROP TABLE account_api_keys");
    auth.refresh(); // should swallow the error and keep last known cache
    assertThat(auth.authenticate(DEV_RAW_KEY)).isPresent();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private void seedDb() {
    jdbc.update("INSERT INTO accounts(account_id, display_name, active) VALUES(?, ?, TRUE)",
        ACCOUNT_ID, "Local Dev Account");
    jdbc.update("INSERT INTO agents(agent_id, account_id, display_name, active) VALUES(?, ?, ?, TRUE)",
        AGENT_ID, ACCOUNT_ID, "Local Dev Agent");
    jdbc.update("INSERT INTO account_api_keys(key_hash, account_id, generation, active) VALUES(?, ?, 1, TRUE)",
        DEV_KEY_HASH, ACCOUNT_ID);
  }
}
