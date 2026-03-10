package com.autotrading.libs.idempotency;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed {@link IdempotencyService} using the {@code idempotency_records} table (V1 migration).
 *
 * <h3>claim() semantics — first-write-wins</h3>
 * <ul>
 *   <li>First caller → INSERT row as PENDING → {@link ClaimOutcome#CLAIMED}
 *   <li>Duplicate client_event_id (any payload, any status) → {@link ClaimOutcome#REPLAY};
 *       the original response is returned to the caller unchanged.
 * </ul>
 */
public class JdbcIdempotencyService implements IdempotencyService {

  private static final Logger log = LoggerFactory.getLogger(JdbcIdempotencyService.class);

  private final JdbcTemplate jdbc;

  public JdbcIdempotencyService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public ClaimResult claim(IdempotencyClaim claim) {
    // Use ON CONFLICT DO NOTHING to avoid poisoning the current transaction with a
    // PostgreSQL aborted-transaction state that would prevent the follow-up SELECT.
    int inserted = jdbc.update(
        """
        INSERT INTO idempotency_records
               (client_event_id, payload_hash, status, created_at_utc, updated_at_utc)
               VALUES (?,               ?,            'PENDING', ?,             ?)
        ON CONFLICT (client_event_id) DO NOTHING
        """,
        claim.key(),
        claim.payloadHash(),
        Timestamp.from(claim.claimedAtUtc()),
        Timestamp.from(claim.claimedAtUtc()));

    if (inserted == 1) {
      IdempotencyRecord rec = new IdempotencyRecord(
          claim.key(), claim.payloadHash(), IdempotencyStatus.PENDING,
          null, null, claim.claimedAtUtc());
      return new ClaimResult(ClaimOutcome.CLAIMED, rec, "claimed");
    }

    // Row already exists — determine the outcome from the existing record.
    Optional<IdempotencyRecord> existing = find(claim.key());
    if (existing.isEmpty()) {
      // Race between two concurrent inserts — treat as replay (first writer won).
      return new ClaimResult(ClaimOutcome.REPLAY, null, "concurrent insert race — first-write-wins");
    }
    // First-write-wins: any duplicate key returns REPLAY regardless of payload or status.
    return new ClaimResult(ClaimOutcome.REPLAY, existing.get(), "first-write-wins: return original response");
  }

  @Override
  @Transactional
  public void markCompleted(String key, String responseSnapshot) {
    jdbc.update(
        """
        UPDATE idempotency_records
           SET status = 'COMPLETED', response_snapshot = ?, updated_at_utc = ?
         WHERE client_event_id = ?
        """,
        responseSnapshot,
        Timestamp.from(Instant.now()),
        key);
  }

  @Override
  @Transactional
  public void markFailed(String key, String failureReason) {
    jdbc.update(
        """
        UPDATE idempotency_records
           SET status = 'FAILED', failure_reason = ?, updated_at_utc = ?
         WHERE client_event_id = ?
        """,
        failureReason,
        Timestamp.from(Instant.now()),
        key);
  }

  @Override
  public Optional<IdempotencyRecord> find(String key) {
    List<IdempotencyRecord> rows = jdbc.query(
        """
        SELECT client_event_id, payload_hash, status, response_snapshot, failure_reason, updated_at_utc
          FROM idempotency_records
         WHERE client_event_id = ?
        """,
        this::mapRow,
        key);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private IdempotencyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new IdempotencyRecord(
        rs.getString("client_event_id"),
        rs.getString("payload_hash"),
        IdempotencyStatus.valueOf(rs.getString("status")),
        rs.getString("response_snapshot"),
        rs.getString("failure_reason"),
        rs.getTimestamp("updated_at_utc").toInstant());
  }
}
