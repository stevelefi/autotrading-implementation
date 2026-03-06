package com.autotrading.libs.reliability.inbox;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC-backed {@link ConsumerInboxRepository} using the {@code consumer_inbox} table.
 * <p>
 * {@link #tryBegin} is idempotent: returns {@code true} on the first call for a
 * given (consumerName, eventId) pair and {@code false} on duplicates (already processed).
 */
public class JdbcConsumerInboxRepository implements ConsumerInboxRepository {

  private static final Logger log = LoggerFactory.getLogger(JdbcConsumerInboxRepository.class);

  private final JdbcTemplate jdbc;

  public JdbcConsumerInboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Atomically inserts a row for the given consumer + event pair.
   *
   * @return {@code true} if this is the first time the consumer has seen this event;
   *         {@code false} if it has already been processed (duplicate → skip).
   */
  @Override
  public boolean tryBegin(String consumerName, String eventId) {
    try {
      jdbc.update(
          "INSERT INTO consumer_inbox (consumer_name, event_id, processed_at_utc) VALUES (?, ?, ?)",
          consumerName,
          eventId,
          Timestamp.from(Instant.now()));
      return true;
    } catch (DuplicateKeyException e) {
      log.debug("consumer_inbox duplicate: consumer={} event={}", consumerName, eventId);
      return false;
    }
  }
}
