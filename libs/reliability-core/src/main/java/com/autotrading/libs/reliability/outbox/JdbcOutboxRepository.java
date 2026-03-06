package com.autotrading.libs.reliability.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed implementation of {@link OutboxRepository}.
 * All operations use the {@code outbox_events} table (V1 migration).
 */
public class JdbcOutboxRepository implements OutboxRepository {

  private static final Logger log = LoggerFactory.getLogger(JdbcOutboxRepository.class);

  private final JdbcTemplate jdbc;

  public JdbcOutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void append(OutboxEvent event) {
    jdbc.update(
        """
        INSERT INTO outbox_events
               (event_id, topic, partition_key, payload_json, status, attempts, created_at_utc, updated_at_utc)
        VALUES (?,         ?,     ?,             ?,            ?,      0,        ?,               ?)
        ON CONFLICT (event_id) DO NOTHING
        """,
        event.eventId(),
        event.topic(),
        event.partitionKey(),
        event.payload(),
        OutboxStatus.NEW.name(),
        java.sql.Timestamp.from(event.createdAtUtc()),
        java.sql.Timestamp.from(event.updatedAtUtc()));
  }

  @Override
  public List<OutboxEvent> pollNew(int batchSize) {
    return jdbc.query(
        """
        SELECT event_id, topic, partition_key, payload_json, status, attempts, last_error,
               created_at_utc, updated_at_utc
          FROM outbox_events
         WHERE status = 'NEW'
         ORDER BY created_at_utc
         LIMIT ?
        """,
        this::mapRow,
        batchSize);
  }

  @Override
  @Transactional
  public void markDispatched(String eventId) {
    jdbc.update(
        """
        UPDATE outbox_events
           SET status = 'DISPATCHED', last_error = NULL, attempts = attempts + 1, updated_at_utc = ?
         WHERE event_id = ?
        """,
        java.sql.Timestamp.from(Instant.now()),
        eventId);
  }

  @Override
  @Transactional
  public void markFailed(String eventId, String error) {
    jdbc.update(
        """
        UPDATE outbox_events
           SET status = 'FAILED', last_error = ?, attempts = attempts + 1, updated_at_utc = ?
         WHERE event_id = ?
        """,
        error,
        java.sql.Timestamp.from(Instant.now()),
        eventId);
  }

  @Override
  public long countPending() {
    Long count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE status = 'NEW'", Long.class);
    return count == null ? 0L : count;
  }

  private OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new OutboxEvent(
        rs.getString("event_id"),
        rs.getString("topic"),
        rs.getString("partition_key"),
        rs.getString("payload_json"),
        OutboxStatus.valueOf(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getString("last_error"),
        rs.getTimestamp("created_at_utc").toInstant(),
        rs.getTimestamp("updated_at_utc").toInstant());
  }
}
