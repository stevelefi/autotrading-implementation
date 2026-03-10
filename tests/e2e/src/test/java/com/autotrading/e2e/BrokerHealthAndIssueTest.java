package com.autotrading.e2e;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.services.ibkr.client.BrokerStatus;
import com.autotrading.services.ibkr.client.IbkrHealthProbe;
import com.autotrading.services.ibkr.client.IbkrRestClient;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.autotrading.services.ibkr.health.BrokerHealthPersister;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Broker health probe state-machine, submit guard, execution deduplication,
 * and BrokerHealthPersister DB-level tests.
 *
 * <p>Groups:
 * <ol>
 *   <li>HP-1 through HP-8  — IbkrHealthProbe status transitions and callbacks</li>
 *   <li>BE-1 through BE-7  — BrokerConnectorEngine submit guard and idempotency</li>
 *   <li>EXEC-1 through EXEC-3 — Execution deduplication</li>
 *   <li>PERSIST-1 through PERSIST-4 — BrokerHealthPersister DB persistence</li>
 * </ol>
 */
class BrokerHealthAndIssueTest {

  // Shared embedded postgres (started once for the full class)
  static EmbeddedPostgres pg;

  @BeforeAll
  static void startEmbeddedPostgres() throws Exception {
    pg = EmbeddedPostgres.start();
    Flyway.configure()
        .dataSource(pg.getPostgresDatabase())
        .locations("filesystem:../../db/migrations")
        .load()
        .migrate();
  }

  @AfterAll
  static void stopEmbeddedPostgres() throws Exception {
    if (pg != null) pg.close();
  }

  // ──────────────────────────────────────────────────────────────────────
  // Factories / helpers
  // ──────────────────────────────────────────────────────────────────────

  /** Returns a mock REST client that reports broker as authenticated + connected. */
  private IbkrRestClient authenticatedClient() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    var auth = new IbkrRestClient.AuthStatus(true, true, false, "ok");
    var iserver = new IbkrRestClient.IServerStatus(auth);
    when(client.tickle()).thenReturn(new IbkrRestClient.TickleResponse(iserver, "sess-ok"));
    return client;
  }

  /** Returns a mock REST client that reports broker as unauthenticated. */
  private IbkrRestClient unauthenticatedClient() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    var auth = new IbkrRestClient.AuthStatus(false, false, false, "not authenticated");
    var iserver = new IbkrRestClient.IServerStatus(auth);
    when(client.tickle()).thenReturn(new IbkrRestClient.TickleResponse(iserver, null));
    return client;
  }

  /** Returns a mock REST client whose tickle() always throws a network exception. */
  private IbkrRestClient throwingClient() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    when(client.tickle()).thenThrow(new RuntimeException("network timeout"));
    return client;
  }

  /** Builds a probe with a transition-recorder callback. */
  private IbkrHealthProbe probeWithTracker(IbkrRestClient client, boolean simulatorMode,
      List<BrokerStatus> transitions) {
    return new IbkrHealthProbe(client, 60_000L, simulatorMode, transitions::add);
  }

  /** Returns a probe that has been driven to UP via a successful tickle. */
  private IbkrHealthProbe upProbe() {
    IbkrHealthProbe probe = new IbkrHealthProbe(authenticatedClient(), 60_000L, false);
    probe.runTickle();
    return probe;
  }

  /** Returns a probe that has been driven to DOWN via a failing tickle. */
  private IbkrHealthProbe downProbe() {
    IbkrHealthProbe probe = new IbkrHealthProbe(throwingClient(), 60_000L, false);
    probe.runTickle();
    return probe;
  }

  /** Builds a BrokerConnectorEngine wired to the given probe. */
  private BrokerConnectorEngine engineWithProbe(IbkrHealthProbe probe, boolean simulatorMode) {
    BrokerOrderRepository repoMock = mock(BrokerOrderRepository.class);
    lenient().when(repoMock.save(any())).thenAnswer(inv -> inv.getArgument(0));
    return new BrokerConnectorEngine(
        new InMemoryIdempotencyService(),
        repoMock,
        mock(ExecutionRepository.class),
        mock(DirectKafkaPublisher.class),
        new ObjectMapper(),
        probe,
        mock(IbkrRestClient.class),
        simulatorMode);
  }

  /** Builds a minimal SubmitOrderRequest. */
  private SubmitOrderRequest submitRequest(String idemKey, String orderIntentId) {
    return SubmitOrderRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-test")
            .setRequestId("req-test")
            .setClientEventId(idemKey)
            .setPrincipalId("svc-test")
            .build())
        .setAgentId("agent-test")
        .setInstrumentId("eq_tqqq")
        .setOrderIntentId(orderIntentId)
        .setSide("BUY").setQty(10)
        .setOrderType("MKT").setTimeInForce("DAY")
        .build();
  }

  /** Reads the current broker status from the embedded postgres. */
  private String readStoredBrokerStatus() throws Exception {
    try (Connection conn = pg.getPostgresDatabase().getConnection();
         ResultSet rs = conn.createStatement().executeQuery(
             "SELECT status FROM broker_health_status WHERE broker_id = 'ibkr'")) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  /** Clears the broker_health_status row so persister tests are isolated. */
  private void clearBrokerHealthRow() throws Exception {
    try (Connection conn = pg.getPostgresDatabase().getConnection()) {
      conn.createStatement().execute(
          "DELETE FROM broker_health_status WHERE broker_id = 'ibkr'");
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  // Group 1: IbkrHealthProbe — status transitions and callbacks
  // ══════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("HP-1: Authenticated tickle → status=UP, isUp()=true (production mode)")
  void healthProbe_authenticatedTickle_statusBecomesUp() {
    IbkrHealthProbe probe = probeWithTracker(authenticatedClient(), false, new ArrayList<>());
    probe.runTickle();

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UP);
    assertThat(probe.isUp()).isTrue();
  }

  @Test
  @DisplayName("HP-2: Unauthenticated tickle → status=DOWN, isUp()=false")
  void healthProbe_unauthenticatedTickle_statusBecomesDown() {
    IbkrHealthProbe probe = probeWithTracker(unauthenticatedClient(), false, new ArrayList<>());
    probe.runTickle();

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);
    assertThat(probe.isUp()).isFalse();
  }

  @Test
  @DisplayName("HP-3: Tickle throws network exception → status=DOWN, isUp()=false")
  void healthProbe_tickleException_statusBecomesDown() {
    IbkrHealthProbe probe = probeWithTracker(throwingClient(), false, new ArrayList<>());
    probe.runTickle();

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);
    assertThat(probe.isUp()).isFalse();
  }

  @Test
  @DisplayName("HP-4: UNKNOWN→UP fires transition callback exactly once; repeated UP does not re-fire")
  void healthProbe_unknownToUp_callbackFiresOnce() {
    List<BrokerStatus> transitions = new ArrayList<>();
    IbkrHealthProbe probe = probeWithTracker(authenticatedClient(), false, transitions);

    probe.runTickle(); // UNKNOWN → UP
    probe.runTickle(); // stays UP — no second callback

    assertThat(transitions).containsExactly(BrokerStatus.UP);
  }

  @Test
  @DisplayName("HP-5: UP→DOWN fires transition callback; repeated DOWN does not re-fire")
  void healthProbe_upToDown_callbackFiresOnce_noRepeat() {
    List<BrokerStatus> transitions = new ArrayList<>();
    IbkrRestClient client = authenticatedClient();
    IbkrHealthProbe probe = probeWithTracker(client, false, transitions);

    probe.runTickle(); // UNKNOWN → UP (callback: UP)

    when(client.tickle()).thenThrow(new RuntimeException("link dropped"));
    probe.runTickle(); // UP → DOWN (callback: DOWN)
    probe.runTickle(); // stays DOWN — no third callback

    assertThat(transitions).containsExactly(BrokerStatus.UP, BrokerStatus.DOWN);
  }

  @Test
  @DisplayName("HP-6: DOWN→UP recovery fires transition callback")
  void healthProbe_downToUp_recoveryCallbackFires() {
    List<BrokerStatus> transitions = new ArrayList<>();
    IbkrRestClient client = throwingClient();
    IbkrHealthProbe probe = probeWithTracker(client, false, transitions);

    probe.runTickle(); // UNKNOWN → DOWN (callback: DOWN)

    // Simulate broker recovery — use doReturn to avoid invoking the throwing stub during re-stubbing
    var auth = new IbkrRestClient.AuthStatus(true, true, false, "recovered");
    doReturn(new IbkrRestClient.TickleResponse(new IbkrRestClient.IServerStatus(auth), "sess-new"))
        .when(client).tickle();
    probe.runTickle(); // DOWN → UP (callback: UP)

    assertThat(transitions).containsExactly(BrokerStatus.DOWN, BrokerStatus.UP);
    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UP);
    assertThat(probe.isUp()).isTrue();
  }

  @Test
  @DisplayName("HP-7: Simulator mode — UNKNOWN status treated as UP (startup race avoidance)")
  void healthProbe_simulatorMode_unknownTreatedAsUp() {
    // No tickle run — probe stays at initial UNKNOWN
    IbkrHealthProbe probe = new IbkrHealthProbe(mock(IbkrRestClient.class), 60_000L, true);

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UNKNOWN);
    assertThat(probe.isUp()).isTrue(); // simulator: UNKNOWN != DOWN → true
  }

  @Test
  @DisplayName("HP-8: Simulator mode — DOWN status blocks isUp() even in simulator")
  void healthProbe_simulatorMode_downBlocksIsUp() {
    List<BrokerStatus> transitions = new ArrayList<>();
    IbkrHealthProbe probe = probeWithTracker(throwingClient(), true /* simulator */, transitions);
    probe.runTickle(); // drives to DOWN

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);
    assertThat(probe.isUp()).isFalse(); // DOWN is always blocked, even in simulator
  }

  // ══════════════════════════════════════════════════════════════════════
  // Group 2: BrokerConnectorEngine — submit guard and idempotency
  // ══════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("BE-1: Broker DOWN in non-simulator mode → submit returns COMMAND_STATUS_FAILED, zero submits recorded")
  void engine_brokerDown_nonSimulator_submitReturnsFailed() {
    BrokerConnectorEngine engine = engineWithProbe(downProbe(), false /* non-simulator */);

    var response = engine.submit(submitRequest("idem-be1", "ord-be1"));

    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_FAILED);
    assertThat(response.getBrokerSubmitId()).isEmpty();
    assertThat(engine.totalSubmitCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("BE-2: Broker UP (simulator mode) → submit returns COMMAND_STATUS_ACCEPTED with a broker submit ID")
  void engine_brokerUp_submitReturnsAccepted() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true /* simulator */);

    var response = engine.submit(submitRequest("idem-be2", "ord-be2"));

    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(response.getBrokerSubmitId()).startsWith("broker-submit-");
    assertThat(response.getSubmittedAt()).isNotBlank();
    assertThat(engine.totalSubmitCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("BE-3: Duplicate client_event_id → second submit returns COMMAND_STATUS_DUPLICATE; only one actual submit")
  void engine_duplicateClientEventId_returnsReplay() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true);

    var first  = engine.submit(submitRequest("idem-be3-dup", "ord-be3"));
    var second = engine.submit(submitRequest("idem-be3-dup", "ord-be3"));

    assertThat(first.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(second.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_DUPLICATE);
    assertThat(engine.totalSubmitCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("BE-4: Submit count tracked per order intent; totalSubmitCount sums all intents")
  void engine_submitCount_trackedPerOrderIntent() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true);

    engine.submit(submitRequest("idem-be4-A", "ord-be4-A"));
    engine.submit(submitRequest("idem-be4-B", "ord-be4-B"));

    assertThat(engine.submitCount("ord-be4-A")).isEqualTo(1);
    assertThat(engine.submitCount("ord-be4-B")).isEqualTo(1);
    assertThat(engine.submitCount("ord-nonexistent")).isEqualTo(0);
    assertThat(engine.totalSubmitCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("BE-5: Cancel always returns COMMAND_STATUS_ACCEPTED with a non-blank cancel ID")
  void engine_cancel_returnsAccepted() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true);

    var response = engine.cancel(CancelOrderRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-cancel").setRequestId("req-cancel")
            .setClientEventId("idem-be5").setPrincipalId("svc-test")
            .build())
        .setAgentId("agent-test").setOrderIntentId("ord-be5").setReason("user cancel")
        .build());

    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(response.getBrokerCancelId()).startsWith("broker-cancel-");
  }

  @Test
  @DisplayName("BE-6: Replace always returns COMMAND_STATUS_ACCEPTED with a non-blank replace ID")
  void engine_replace_returnsAccepted() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true);

    var response = engine.replace(ReplaceOrderRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-replace").setRequestId("req-replace")
            .setClientEventId("idem-be6").setPrincipalId("svc-test")
            .build())
        .setAgentId("agent-test").setOrderIntentId("ord-be6")
        .setNewQty(20).setNewLimitPrice("100.50").setReason("price update")
        .build());

    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(response.getBrokerReplaceId()).startsWith("broker-replace-");
  }

  @Test
  @DisplayName("BE-7: Simulator-mode engine bypasses broker-down guard — submit proceeds even when probe is DOWN")
  void engine_simulatorMode_bypassesBrokerDownGuard() {
    // Probe driven to DOWN; engine is in simulator mode (guard is `!simulatorMode && !isUp()`)
    IbkrHealthProbe downSimProbe = probeWithTracker(throwingClient(), true, new ArrayList<>());
    downSimProbe.runTickle(); // drives to DOWN

    assertThat(downSimProbe.isUp()).isFalse(); // confirmed DOWN

    BrokerConnectorEngine engine = engineWithProbe(downSimProbe, true /* simulatorMode */);
    var response = engine.submit(submitRequest("idem-be7", "ord-be7"));

    // Guard is `if (!simulatorMode && ...) → false` in simulator mode, so submit proceeds
    assertThat(response.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
  }

  // ══════════════════════════════════════════════════════════════════════
  // Group 3: Execution deduplication
  // ══════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("EXEC-1: Same execId recorded twice → second call returns false (deduped in-memory)")
  void engine_duplicateExecId_rejected() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true);

    assertThat(engine.recordExecution("exec-dedup-A")).isTrue();
    assertThat(engine.recordExecution("exec-dedup-A")).isFalse();
  }

  @Test
  @DisplayName("EXEC-2: Distinct execIds all accepted and tracked independently")
  void engine_distinctExecIds_allRecorded() {
    BrokerConnectorEngine engine = engineWithProbe(upProbe(), true);

    assertThat(engine.recordExecution("exec-X")).isTrue();
    assertThat(engine.recordExecution("exec-Y")).isTrue();
    assertThat(engine.recordExecution("exec-Z")).isTrue();
  }

  @Test
  @DisplayName("EXEC-3: New engine instance does not inherit exec dedup state — in-memory set is fresh (mirrors DB boundary)")
  void engine_newInstance_doesNotInheritExecDedupeState() {
    BrokerConnectorEngine engine1 = engineWithProbe(upProbe(), true);
    engine1.recordExecution("exec-cross-instance");

    // Production uses DB-backed idempotency; the in-memory set resets on restart
    BrokerConnectorEngine engine2 = engineWithProbe(upProbe(), true);
    assertThat(engine2.recordExecution("exec-cross-instance")).isTrue();
  }

  // ══════════════════════════════════════════════════════════════════════
  // Group 4: BrokerHealthPersister — DB persistence (embedded postgres)
  // ══════════════════════════════════════════════════════════════════════

  @Test
  @DisplayName("PERSIST-1: onTransition(UP) inserts broker_health_status row with status=UP")
  void persister_onTransitionUp_insertsRow() throws Exception {
    clearBrokerHealthRow();

    new BrokerHealthPersister(new NamedParameterJdbcTemplate(pg.getPostgresDatabase()))
        .onTransition(BrokerStatus.UP);

    assertThat(readStoredBrokerStatus()).isEqualTo("UP");
  }

  @Test
  @DisplayName("PERSIST-2: onTransition(DOWN) after UP upserts row — status updated to DOWN")
  void persister_onTransitionDown_updatesExistingRow() throws Exception {
    clearBrokerHealthRow();
    BrokerHealthPersister persister =
        new BrokerHealthPersister(new NamedParameterJdbcTemplate(pg.getPostgresDatabase()));

    persister.onTransition(BrokerStatus.UP);
    persister.onTransition(BrokerStatus.DOWN);

    assertThat(readStoredBrokerStatus()).isEqualTo("DOWN");
  }

  @Test
  @DisplayName("PERSIST-3: onTransition(UNKNOWN) persists UNKNOWN status correctly")
  void persister_onTransitionUnknown_persistsUnknown() throws Exception {
    clearBrokerHealthRow();

    new BrokerHealthPersister(new NamedParameterJdbcTemplate(pg.getPostgresDatabase()))
        .onTransition(BrokerStatus.UNKNOWN);

    assertThat(readStoredBrokerStatus()).isEqualTo("UNKNOWN");
  }

  @Test
  @DisplayName("PERSIST-4: onTransition with broken JDBC swallows exception — broker ops are not disrupted")
  void persister_brokenJdbc_swallowsException() {
    // Use a broken JDBC template that throws on every update call
    NamedParameterJdbcTemplate brokenJdbc = mock(NamedParameterJdbcTemplate.class);
    when(brokenJdbc.update(anyString(), any(SqlParameterSource.class)))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("connection refused"));

    BrokerHealthPersister persister = new BrokerHealthPersister(brokenJdbc);

    // Must not propagate — the persister swallows DB failures to keep broker ops alive
    assertThatCode(() -> persister.onTransition(BrokerStatus.DOWN))
        .doesNotThrowAnyException();
  }
}
