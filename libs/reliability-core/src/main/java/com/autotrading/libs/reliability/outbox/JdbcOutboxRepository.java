package com.autotrading.libs.reliability.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed implementation of {@link OutboxRepository}.
 * All operations use the {@code outbox_events} table (V1 + V3 migrations).
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
               (event_id, topic, partition_key, payload_json, status, attempts,
                next_retry_at, created_at_utc, updated_at_utc)
        VALUES (?,         ?,     ?,             ?,            ?,      0,
                ?,             ?,               ?)
        ON CONFLICT (event_id) DO NOTHING
        """,
        event.eventId(),
        event.topic(),
        event.partitionKey(),
        event.payload(),
        OutboxStatus.NEW.name(),
        event.nextRetryAt() != null ? Timestamp.from(event.nextRetryAt()) : null,
        Timestamp.from(event.createdAtUtc()),
        Timestamp.from(event.updatedAtUtc()));
  }

  /** @deprecated Use {@link #pollRetryable(int)} instead. */
  @Deprecated
  @Override
  @Transactional
  public List<OutboxEvent> pollNew(int batchSize) {
    return jdbc.query(
        """
        SELECT event_id, topic, partition_key, payload_json, status, attempts, last_error,
               next_retry_at, created_at_utc, updated_at_utc
          FROM outbox_events
         WHERE status = 'NEW'
         ORDER BY created_at_utc
           FOR UPDATE SKIP LOCKED
         LIMIT ?
        """,
        this::mapRow,
        batchSize);
  }

  @Override
  @Transactional
  public List<OutboxEvent> pollRetryable(int batchSize) {
    return jdbc.query(
        """
        SELECT event_id, topic, partition_key, payload_json, status, attempts, last_error,
               next_retry_at, created_at_utc, updated_at_utc
          FROM outbox_events
         WHERE status IN ('NEW', 'FAILED')
           AND (next_retry_at IS NULL OR next_retry_at <= NOW())
         ORDER BY created_at_utc
           FOR UPDATE SKIP LOCKED
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
           SET status = 'DISPATCHED', last_error = NULL, attempts = attempts + 1,
               next_retry_at = NULL, updated_at_utc = ?
         WHERE event_id = ?
        """,
        Timestamp.from(Instant.now()),
        eventId);
  }

  @Override
  @Transactional
  public void markFailed(String eventId, String error, Instant nextRetryAt) {
    jdbc.update(
        """
        UPDATE outbox_events
           SET status = 'FAILED', last_error = ?, attempts = attempts + 1,
               next_retry_at = ?, updated_at_utc = ?
         WHERE event_id = ?
        """,
        error,
        nextRetryAt != null ? Timestamp.from(nextRetryAt) : null,
        Timestamp.from(Instant.now()),
        eventId);
  }

  @Override
  public long countPending() {
    Long count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE status IN ('NEW', 'FAILED') AND (next_retry_at IS NULL OR next_retry_at <= NOW())",
        Long.class);
    return count == null ? 0L : count;
  }

  private OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp nextRetryTs = rs.getTimestamp("next_retry_at");
    return new OutboxEvent(
        rs.getString("event_id"),
        rs.getString("topic"),
        rs.getString("partition_key"),
        rs.getString("payload_json"),
        OutboxStatus.valueOf(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getString("last_error"),
        nextRetryTs != null ? nextRetryTs.toInstant() : null,
        rs.getTimestamp("created_at_utc").toInstant(),
        rs.getTimestamp("updated_at_utc").toInstant());
  }
}
