package com.autotrading.libs.idempotency;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed {@link IdempotencyService} using the {@code idempotency_records} table (V1 migration).
 *
 * <h3>claim() semantics</h3>
 * <ul>
 *   <li>First caller → INSERT row as PENDING → {@link ClaimOutcome#CLAIMED}
 *   <li>Duplicate key with matching payload_hash and status COMPLETED/FAILED → {@link ClaimOutcome#REPLAY}
 *   <li>Duplicate key with different payload_hash or still PENDING → {@link ClaimOutcome#CONFLICT}
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
    try {
      jdbc.update(
          """
          INSERT INTO idempotency_records
                 (idempotency_key, payload_hash, status, created_at_utc, updated_at_utc)
          VALUES (?,                ?,            'PENDING', ?,             ?)
          """,
          claim.key(),
          claim.payloadHash(),
          Timestamp.from(claim.claimedAtUtc()),
          Timestamp.from(claim.claimedAtUtc()));

      IdempotencyRecord rec = new IdempotencyRecord(
          claim.key(), claim.payloadHash(), IdempotencyStatus.PENDING,
          null, null, claim.claimedAtUtc());
      return new ClaimResult(ClaimOutcome.CLAIMED, rec, "claimed");

    } catch (DuplicateKeyException e) {
      Optional<IdempotencyRecord> existing = find(claim.key());
      if (existing.isEmpty()) {
        return new ClaimResult(ClaimOutcome.CONFLICT, null, "concurrent insert race");
      }
      IdempotencyRecord rec = existing.get();
      if (!rec.payloadHash().equals(claim.payloadHash())) {
        return new ClaimResult(ClaimOutcome.CONFLICT, rec, "payload hash mismatch");
      }
      if (rec.status() == IdempotencyStatus.PENDING) {
        return new ClaimResult(ClaimOutcome.CONFLICT, rec, "in-flight duplicate");
      }
      return new ClaimResult(ClaimOutcome.REPLAY, rec, "replay");
    }
  }

  @Override
  @Transactional
  public void markCompleted(String key, String responseSnapshot) {
    jdbc.update(
        """
        UPDATE idempotency_records
           SET status = 'COMPLETED', response_snapshot = ?, updated_at_utc = ?
         WHERE idempotency_key = ?
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
         WHERE idempotency_key = ?
        """,
        failureReason,
        Timestamp.from(Instant.now()),
        key);
  }

  @Override
  public Optional<IdempotencyRecord> find(String key) {
    List<IdempotencyRecord> rows = jdbc.query(
        """
        SELECT idempotency_key, payload_hash, status, response_snapshot, failure_reason, updated_at_utc
          FROM idempotency_records
         WHERE idempotency_key = ?
        """,
        this::mapRow,
        key);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private IdempotencyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new IdempotencyRecord(
        rs.getString("idempotency_key"),
        rs.getString("payload_hash"),
        IdempotencyStatus.valueOf(rs.getString("status")),
        rs.getString("response_snapshot"),
        rs.getString("failure_reason"),
        rs.getTimestamp("updated_at_utc").toInstant());
  }
}
