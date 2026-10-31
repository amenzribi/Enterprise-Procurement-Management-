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

	Optional<CredentialFlags> flags(long userId);

	void savePasswordHash(long userId, String passwordHash);

	void savePasswordHash(long userId, String passwordHash, boolean mustChangePassword);

	void clearMustChange(long userId);

	record StoredCredential(long userId, String email, String passwordHash, boolean active, boolean demo,
			int credentialsVersion, boolean mustChangePassword) {
	}

	record CredentialFlags(int credentialsVersion, boolean mustChangePassword) {
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
					SELECT u.id, u.email, c.password_hash, u.is_active, u.is_demo, u.credentials_version,
					       c.must_change_password
					FROM user_account u JOIN user_credential c ON c.user_id = u.id
					WHERE LOWER(u.email) = LOWER(?)""",
					(rs, i) -> new StoredCredential(rs.getLong("id"), rs.getString("email"),
							rs.getString("password_hash"), rs.getBoolean("is_active"), rs.getBoolean("is_demo"),
							rs.getInt("credentials_version"), rs.getBoolean("must_change_password")),
					email).stream().findFirst();
		}

		@Override
		public Optional<CredentialFlags> flags(long userId) {
			return jdbc.query("""
					SELECT u.credentials_version, c.must_change_password
					FROM user_account u JOIN user_credential c ON c.user_id = u.id WHERE u.id = ?""",
					(rs, i) -> new CredentialFlags(rs.getInt("credentials_version"),
							rs.getBoolean("must_change_password")),
					userId).stream().findFirst();
		}

		@Override
		public void savePasswordHash(long userId, String passwordHash) {
			savePasswordHash(userId, passwordHash, false);
		}

		@Override
		public void savePasswordHash(long userId, String passwordHash, boolean mustChangePassword) {
			int updated = jdbc.update("""
					UPDATE user_credential SET password_hash = ?, password_changed_at = SYSUTCDATETIME(),
					       must_change_password = ? WHERE user_id = ?""", passwordHash, mustChangePassword ? 1 : 0,
					userId);
			if (updated == 0) {
				jdbc.update("""
						INSERT INTO user_credential (user_id, password_hash, must_change_password)
						VALUES (?, ?, ?)""", userId, passwordHash, mustChangePassword ? 1 : 0);
			}
		}

		@Override
		public void clearMustChange(long userId) {
			jdbc.update("UPDATE user_credential SET must_change_password = 0 WHERE user_id = ?", userId);
		}

	}

}
