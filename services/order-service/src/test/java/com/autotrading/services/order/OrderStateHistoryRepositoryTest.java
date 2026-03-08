package com.autotrading.services.order;

import com.autotrading.services.order.db.OrderStateHistoryEntity;
import com.autotrading.services.order.db.OrderStateHistoryRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link OrderStateHistoryRepository} using embedded PostgreSQL —
 * no Spring context, no Docker needed.
 */
class OrderStateHistoryRepositoryTest {

    static EmbeddedPostgres pg;

    private OrderStateHistoryRepository repository;

    @BeforeAll
    static void createSchema() throws Exception {
        pg = EmbeddedPostgres.start();
        new NamedParameterJdbcTemplate(pg.getPostgresDatabase()).getJdbcTemplate().execute(
                "CREATE TABLE IF NOT EXISTS order_state_history ("
                + "  order_intent_id TEXT        NOT NULL,"
                + "  sequence_no     BIGINT       NOT NULL,"
                + "  from_state      TEXT,"
                + "  to_state        TEXT         NOT NULL,"
                + "  reason          TEXT,"
                + "  trace_id        TEXT,"
                + "  occurred_at     TIMESTAMP(6) NOT NULL,"
                + "  PRIMARY KEY (order_intent_id, sequence_no)"
                + ")");
    }

    @AfterAll
    static void stopDb() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(pg.getPostgresDatabase());
        repository = new OrderStateHistoryRepository(jdbc);
    }

    @Test
    void save_assigns_sequence_starting_at_1() {
        OrderStateHistoryEntity e = new OrderStateHistoryEntity(
                "order-1", 0L, null, "PENDING_SUBMISSION", "created", "trace-1", Instant.now());
        repository.save(e);

        List<OrderStateHistoryEntity> rows = repository.findByOrderIntentId("order-1");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSequenceNo()).isEqualTo(1L);
    }

    @Test
    void save_increments_sequence_on_each_append() {
        Instant now = Instant.now();
        repository.save(new OrderStateHistoryEntity(
                "order-2", 0L, null, "PENDING_SUBMISSION", "init", "t", now));
        repository.save(new OrderStateHistoryEntity(
                "order-2", 0L, "PENDING_SUBMISSION", "SUBMITTED", "accepted", "t", now));
        repository.save(new OrderStateHistoryEntity(
                "order-2", 0L, "SUBMITTED", "FILLED", "fill", "t", now));

        List<OrderStateHistoryEntity> rows = repository.findByOrderIntentId("order-2");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getSequenceNo()).isEqualTo(1L);
        assertThat(rows.get(1).getSequenceNo()).isEqualTo(2L);
        assertThat(rows.get(2).getSequenceNo()).isEqualTo(3L);
    }

    @Test
    void findByOrderIntentId_returns_only_matching_order() {
        Instant now = Instant.now();
        repository.save(new OrderStateHistoryEntity(
                "order-A", 0L, null, "PENDING_SUBMISSION", "a", "t", now));
        repository.save(new OrderStateHistoryEntity(
                "order-B", 0L, null, "PENDING_SUBMISSION", "b", "t", now));

        assertThat(repository.findByOrderIntentId("order-A")).hasSize(1);
        assertThat(repository.findByOrderIntentId("order-B")).hasSize(1);
        assertThat(repository.findByOrderIntentId("order-MISSING")).isEmpty();
    }

    @Test
    void backwardCompatAlias_delegates_to_findByOrderIntentId() {
        Instant now = Instant.now();
        repository.save(new OrderStateHistoryEntity(
                "order-C", 0L, null, "PENDING_SUBMISSION", "c", "t", now));

        List<OrderStateHistoryEntity> via = repository
                .findByIdOrderIntentIdOrderByIdSequenceNoAsc("order-C");
        assertThat(via).hasSize(1);
        assertThat(via.get(0).getOrderIntentId()).isEqualTo("order-C");
    }
}
