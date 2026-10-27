package com.epi.procurement.identity;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Local password credentials. Lookup is by e-mail, case-insensitively.
 */
interface CredentialStore {

	Optional<StoredCredential> findByEmail(String email);

	void savePasswordHash(long userId, String passwordHash);

	record StoredCredential(long userId, String email, String passwordHash, boolean active, boolean demo) {
	}

	@Component
	class Jdbc implements CredentialStore {

		private final JdbcTemplate jdbc;

		Jdbc(DataSource dataSource) {
			this.jdbc = new JdbcTemplate(dataSource);
		}

		@Override
		public Optional<StoredCredential> findByEmail(String email) {
			return jdbc.query("""
					SELECT u.id, u.email, c.password_hash, u.is_active, u.is_demo
					FROM user_account u JOIN user_credential c ON c.user_id = u.id
					WHERE LOWER(u.email) = LOWER(?)""", (rs, i) -> new StoredCredential(rs.getLong("id"),
					rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("is_active"),
					rs.getBoolean("is_demo")), email).stream().findFirst();
		}

		@Override
		public void savePasswordHash(long userId, String passwordHash) {
			int updated = jdbc.update("UPDATE user_credential SET password_hash = ?, password_changed_at = SYSUTCDATETIME() "
					+ "WHERE user_id = ?", passwordHash, userId);
			if (updated == 0) {
				jdbc.update("INSERT INTO user_credential (user_id, password_hash) VALUES (?, ?)", userId, passwordHash);
			}
		}

	}

}
