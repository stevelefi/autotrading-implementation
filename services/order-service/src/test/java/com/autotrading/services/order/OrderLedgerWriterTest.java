package com.autotrading.services.order;

import com.autotrading.services.order.db.OrderLedgerWriter;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link OrderLedgerWriter} using embedded PostgreSQL — no Spring context,
 * no Docker needed.
 */
class OrderLedgerWriterTest {

    static EmbeddedPostgres pg;

    private OrderLedgerWriter writer;
    private NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void createSchema() throws Exception {
        pg = EmbeddedPostgres.start();
        new NamedParameterJdbcTemplate(pg.getPostgresDatabase()).getJdbcTemplate().execute(
                "CREATE TABLE IF NOT EXISTS order_ledger ("
                + "  order_intent_id   TEXT         NOT NULL PRIMARY KEY,"
                + "  state             TEXT         NOT NULL,"
                + "  state_version     BIGINT       NOT NULL DEFAULT 1,"
                + "  submission_deadline TIMESTAMP(6),"
                + "  last_status_at    TIMESTAMP(6),"
                + "  updated_at        TIMESTAMP(6) NOT NULL"
                + ")");
    }

    @AfterAll
    static void stopDb() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void setUp() {
        jdbc = new NamedParameterJdbcTemplate(pg.getPostgresDatabase());
        writer = new OrderLedgerWriter(jdbc);
    }

    private void insertLedger(String orderId, String state, long version) {
        jdbc.update(
                "INSERT INTO order_ledger "
                + "(order_intent_id, state, state_version, updated_at) "
                + "VALUES (:id, :state, :version, :now)",
                new MapSqlParameterSource()
                        .addValue("id", orderId)
                        .addValue("state", state)
                        .addValue("version", version)
                        .addValue("now", Timestamp.from(Instant.now())));
    }

    private Map<String, Object> readLedger(String orderId) {
        return jdbc.queryForMap(
                "SELECT * FROM order_ledger WHERE order_intent_id = :id",
                Map.of("id", orderId));
    }

    @Test
    void transition_succeeds_when_version_matches() {
        insertLedger("ol-1", "PENDING_SUBMISSION", 1L);
        writer.transition("ol-1", "SUBMITTED", 1L, null, Instant.now());

        Map<String, Object> row = readLedger("ol-1");
        assertThat(row.get("state")).isEqualTo("SUBMITTED");
        assertThat(((Number) row.get("state_version")).longValue()).isEqualTo(2L);
    }

    @Test
    void transition_throws_on_stale_version() {
        insertLedger("ol-2", "PENDING_SUBMISSION", 1L);
        // first transition succeeds, bumps version to 2
        writer.transition("ol-2", "SUBMITTED", 1L, null, Instant.now());

        // second attempt with stale version 1 must throw
        assertThatThrownBy(() ->
                writer.transition("ol-2", "FILLED", 1L, null, Instant.now()))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("ol-2");
    }

    @Test
    void transition_sets_last_status_at_when_provided() {
        insertLedger("ol-3", "PENDING_SUBMISSION", 1L);
        Instant statusTime = Instant.parse("2024-06-01T12:00:00Z");
        writer.transition("ol-3", "SUBMITTED", 1L, statusTime, Instant.now());

        Map<String, Object> row = readLedger("ol-3");
        assertThat(row.get("last_status_at")).isNotNull();
    }

    @Test
    void transition_increments_version_on_each_call() {
        insertLedger("ol-4", "PENDING_SUBMISSION", 1L);
        Instant now = Instant.now();
        writer.transition("ol-4", "SUBMITTED", 1L, null, now);
        writer.transition("ol-4", "FILLED",    2L, now,  now);

        Map<String, Object> row = readLedger("ol-4");
        assertThat(row.get("state")).isEqualTo("FILLED");
        assertThat(((Number) row.get("state_version")).longValue()).isEqualTo(3L);
    }
}
