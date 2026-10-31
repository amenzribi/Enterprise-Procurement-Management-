package com.epi.procurement.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PasswordResetStore {

	private final JdbcTemplate jdbc;

	PasswordResetStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	void expireActive(long userId, Instant now) {
		jdbc.update("""
				UPDATE password_reset_token SET used_at = ? WHERE user_id = ? AND used_at IS NULL AND expires_at > ?""",
				ts(now), userId, ts(now));
	}

	long insert(long userId, String tokenHash, String purpose, Instant expiresAt, Instant now, Long createdBy) {
		return jdbc.queryForObject("""
				INSERT INTO password_reset_token (user_id, token_hash, purpose, expires_at, created_at, created_by)
				OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?)""", Long.class, userId, tokenHash, purpose, ts(expiresAt),
				ts(now), createdBy);
	}

	Optional<TokenRow> findActiveByHash(String tokenHash, Instant now) {
		return jdbc.query("""
				SELECT id, user_id, purpose, expires_at, used_at FROM password_reset_token
				WHERE RTRIM(token_hash) = ?""", (rs, i) -> new TokenRow(rs.getLong("id"), rs.getLong("user_id"),
				rs.getString("purpose"), rs.getTimestamp("expires_at").toInstant(),
				rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()), tokenHash)
			.stream()
			.findFirst()
			.filter(row -> row.usedAt() == null && row.expiresAt().isAfter(now));
	}

	boolean markUsed(long id, Instant now) {
		return jdbc.update("UPDATE password_reset_token SET used_at = ? WHERE id = ? AND used_at IS NULL", ts(now),
				id) == 1;
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}

	record TokenRow(long id, long userId, String purpose, Instant expiresAt, Instant usedAt) {
	}

}
