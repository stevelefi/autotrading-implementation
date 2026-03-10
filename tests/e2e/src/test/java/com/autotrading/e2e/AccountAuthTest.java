package com.autotrading.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autotrading.libs.auth.ApiKeyAuthenticator;
import com.autotrading.libs.auth.AuthenticatedPrincipal;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * E2E auth tests: exercises {@link ApiKeyAuthenticator} against the real schema
 * seeded by V10 migration, using embedded-postgres so no external DB is needed.
 *
 * <ul>
 *   <li>Happy path — dev seed key resolves to {@code acc-local-dev}
 *   <li>Unknown key → empty Optional (→ 401 in ingress)
 *   <li>Known key, wrong agent → agent not owned (→ 403 in ingress)
 *   <li>Inactive key → empty Optional (→ 401 in ingress)
 * </ul>
 */
class AccountAuthTest {

  static EmbeddedPostgres pg;
  static JdbcTemplate jdbc;

  private static final String DEV_RAW_KEY  = "dev-api-key-do-not-use-in-production";
  private static final String DEV_HASH     = ApiKeyAuthenticator.sha256Hex(DEV_RAW_KEY);
  private static final String ACC_ID       = "acc-local-dev";
  private static final String AGENT_ID     = "agent-local";

  @BeforeAll
  static void startDb() throws Exception {
    pg = EmbeddedPostgres.start();
    Flyway.configure()
        .dataSource(pg.getPostgresDatabase())
        .locations("filesystem:../../db/migrations")
        .load()
        .migrate();

    jdbc = new JdbcTemplate(pg.getPostgresDatabase());
  }

  @AfterAll
  static void stopDb() throws Exception {
    if (pg != null) pg.close();
  }

  // Refresh before each test to ensure cache is current
  ApiKeyAuthenticator auth;

  @BeforeEach
  void buildAuth() {
    auth = new ApiKeyAuthenticator(jdbc, 60_000);
    auth.refresh();
  }

  // ------------------------------------------------------------------
  // 1. Happy path — dev seed key resolves
  // ------------------------------------------------------------------

  @Test
  void devSeedKeyAuthenticatesSuccessfully() {
    var result = auth.authenticate(DEV_RAW_KEY);

    assertThat(result).isPresent();
    AuthenticatedPrincipal principal = result.get();
    assertThat(principal.accountId()).isEqualTo(ACC_ID);
    assertThat(principal.keyHash()).isEqualTo(DEV_HASH);
    assertThat(principal.generation()).isEqualTo(1);
  }

  // ------------------------------------------------------------------
  // 2. Unknown key → 401 path
  // ------------------------------------------------------------------

  @Test
  void unknownRawKeyReturnsEmpty() {
    assertThat(auth.authenticate("some-completely-unknown-key")).isEmpty();
  }

  // ------------------------------------------------------------------
  // 3. Known key, wrong agent → 403 path
  // ------------------------------------------------------------------

  @Test
  void agentOwnedByCorrectAccount() {
    // dev seed agent-local belongs to acc-local-dev
    assertThat(auth.isAgentOwnedBy(AGENT_ID, ACC_ID)).isTrue();
  }

  @Test
  void agentNotOwnedByDifferentAccount() {
    // agent-local does NOT belong to a different account
    assertThat(auth.isAgentOwnedBy(AGENT_ID, "acc-different")).isFalse();
  }

  @Test
  void unknownAgentIsNotOwned() {
    assertThat(auth.isAgentOwnedBy("nonexistent-agent", ACC_ID)).isFalse();
  }

  // ------------------------------------------------------------------
  // 4. Inactive key → 401 path
  // ------------------------------------------------------------------

  @Test
  void deactivatedKeyReturnsEmpty() {
    // Deactivate the dev key, refresh, then verify it no longer resolves
    jdbc.update("UPDATE account_api_keys SET active = FALSE WHERE key_hash = ?", DEV_HASH);
    auth.refresh();

    assertThat(auth.authenticate(DEV_RAW_KEY)).isEmpty();

    // Re-activate so other tests are not affected
    jdbc.update("UPDATE account_api_keys SET active = TRUE WHERE key_hash = ?", DEV_HASH);
    auth.refresh();
  }

  // ------------------------------------------------------------------
  // 5. Inactive account → 401 path
  // ------------------------------------------------------------------

  @Test
  void inactiveAccountKeyReturnsEmpty() {
    jdbc.update("UPDATE accounts SET active = FALSE WHERE account_id = ?", ACC_ID);
    auth.refresh();

    assertThat(auth.authenticate(DEV_RAW_KEY)).isEmpty();

    // Restore
    jdbc.update("UPDATE accounts SET active = TRUE WHERE account_id = ?", ACC_ID);
    auth.refresh();
  }
}
