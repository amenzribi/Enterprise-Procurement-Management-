package com.epi.procurement.identity;

import static com.epi.procurement.platform.persistence.UtcTimes.db;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Rows of {@code email_change_request} (V26). */
@Repository
class EmailChangeStore {

	static final String PENDING_APPROVAL = "PENDING_APPROVAL";

	static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";

	record Change(long id, long userId, String oldEmail, String newEmail, String reason, String status,
			long requestedBy, Instant requestedAt, Long approvedBy, Instant tokenExpiresAt) {

		boolean open() {
			return PENDING_APPROVAL.equals(status) || PENDING_VERIFICATION.equals(status);
		}

	}

	private static final RowMapper<Change> CHANGE = (rs, i) -> new Change(rs.getLong("id"), rs.getLong("user_id"),
			rs.getString("old_email"), rs.getString("new_email"), rs.getString("reason"), rs.getString("status"),
			rs.getLong("requested_by_user_id"), instant(rs.getObject("requested_at", java.time.LocalDateTime.class)),
			rs.getObject("approved_by_user_id", Long.class),
			instant(rs.getObject("token_expires_at", java.time.LocalDateTime.class)));

	private final JdbcTemplate jdbc;

	EmailChangeStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	private static Instant instant(java.time.LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	/** The open change of an account, locked for update, if any. */
	Optional<Change> openForUpdate(long userId) {
		return jdbc.query("SELECT * FROM email_change_request WITH (UPDLOCK, ROWLOCK) WHERE user_id = ? AND status IN "
				+ "('PENDING_APPROVAL', 'PENDING_VERIFICATION')", CHANGE, userId).stream().findFirst();
	}

	Optional<Change> open(long userId) {
		return jdbc.query("SELECT * FROM email_change_request WHERE user_id = ? AND status IN "
				+ "('PENDING_APPROVAL', 'PENDING_VERIFICATION')", CHANGE, userId).stream().findFirst();
	}

	Optional<Change> find(long id) {
		return jdbc.query("SELECT * FROM email_change_request WHERE id = ?", CHANGE, id).stream().findFirst();
	}

	/** The change waiting for proof of control whose token hashes to {@code hash}, locked for update. */
	Optional<Change> byTokenForUpdate(String hash) {
		return jdbc.query("SELECT * FROM email_change_request WITH (UPDLOCK, ROWLOCK) WHERE token_hash = ? "
				+ "AND status = 'PENDING_VERIFICATION'", CHANGE, hash).stream().findFirst();
	}

	/** True when the address belongs to another account or is the target of another open change. */
	boolean addressTaken(String email, long userId) {
		Integer accounts = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE email = ? AND id <> ?",
				Integer.class, email, userId);
		Integer changes = jdbc.queryForObject("SELECT COUNT(*) FROM email_change_request WHERE new_email = ? AND user_id <> ? "
				+ "AND status IN ('PENDING_APPROVAL', 'PENDING_VERIFICATION')", Integer.class, email, userId);
		return accounts > 0 || changes > 0;
	}

	long insert(long userId, String oldEmail, String newEmail, String reason, String status, long requestedBy,
			Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_email_change_request", Long.class);
		jdbc.update("""
				INSERT INTO email_change_request (id, user_id, old_email, new_email, reason, status, requested_by_user_id,
				                                  requested_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, userId, oldEmail, newEmail, reason, status, requestedBy, db(now));
		return id;
	}

	void approve(long id, long approverId, Instant now) {
		jdbc.update("UPDATE email_change_request SET status = 'PENDING_VERIFICATION', approved_by_user_id = ?, "
				+ "approved_at = ? WHERE id = ? AND status = 'PENDING_APPROVAL'", approverId, db(now), id);
	}

	/** Stores the hash of a newly sent token; an earlier token of the same change stops working. */
	boolean setToken(long id, String hash, Instant expiresAt) {
		return jdbc.update("UPDATE email_change_request SET token_hash = ?, token_expires_at = ? WHERE id = ? "
				+ "AND status = 'PENDING_VERIFICATION'", hash, db(expiresAt), id) == 1;
	}

	void changeAccountEmail(long userId, String email, Instant now) {
		jdbc.update("UPDATE user_account SET email = ?, updated_at = ?, version = version + 1 WHERE id = ?", email, db(now),
				userId);
	}

	void close(long id, String status, Long actorId, Instant now) {
		jdbc.update("UPDATE email_change_request SET status = ?, closed_at = ?, closed_by_user_id = ?, token_hash = NULL "
				+ "WHERE id = ?", status, db(now), actorId, id);
	}

}
