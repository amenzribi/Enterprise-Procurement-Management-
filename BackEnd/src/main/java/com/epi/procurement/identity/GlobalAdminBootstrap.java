package com.epi.procurement.identity;

import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Production one-shot bootstrap of the first GLOBAL_ADMIN when {@code GLOBAL_ADMIN_BOOTSTRAP_PASSWORD} is set and no
 * GLOBAL_ADMIN exists yet (S10-AC-03). Never runs a default password. Does not recreate accounts on later restarts
 * once {@code identity_bootstrap} is recorded or a GLOBAL_ADMIN already exists. Demo profile uses
 * {@link DemoUserSeeder} instead.
 */
@Component
@Order(50)
class GlobalAdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(GlobalAdminBootstrap.class);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final CredentialStore credentials;

	private final PasswordEncoder passwordEncoder;

	private final PasswordPolicy policy;

	private final String bootstrapPassword;

	private final String bootstrapEmail;

	private final boolean demoProfile;

	GlobalAdminBootstrap(DataSource dataSource, PlatformTransactionManager tx, CredentialStore credentials,
			PasswordEncoder passwordEncoder, PasswordPolicy policy,
			@Value("${GLOBAL_ADMIN_BOOTSTRAP_PASSWORD:}") String bootstrapPassword,
			@Value("${GLOBAL_ADMIN_BOOTSTRAP_EMAIL:global.admin@localhost}") String bootstrapEmail,
			org.springframework.core.env.Environment environment) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.transaction = new TransactionTemplate(tx);
		this.credentials = credentials;
		this.passwordEncoder = passwordEncoder;
		this.policy = policy;
		this.bootstrapPassword = bootstrapPassword;
		this.bootstrapEmail = bootstrapEmail;
		this.demoProfile = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("demo"));
	}

	@Override
	public void run(ApplicationArguments args) {
		if (demoProfile) {
			return;
		}
		if (!StringUtils.hasText(bootstrapPassword)) {
			return;
		}
		Integer existing = jdbc.queryForObject("""
				SELECT COUNT(*) FROM user_role r JOIN user_account u ON u.id = r.user_id
				WHERE r.role = 'GLOBAL_ADMIN' AND u.is_active = 1""", Integer.class);
		if (existing != null && existing > 0) {
			log.info("GLOBAL_ADMIN bootstrap skipped: an active global administrator already exists.");
			return;
		}
		Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM identity_bootstrap WHERE id = 1", Integer.class);
		if (done != null && done > 0) {
			log.warn("GLOBAL_ADMIN bootstrap skipped: identity_bootstrap already completed and no active GLOBAL_ADMIN remains. Restore access out-of-band.");
			return;
		}
		policy.validate(bootstrapPassword);
		Long companyId = jdbc.query("""
				SELECT TOP 1 id FROM company WHERE is_active = 1 ORDER BY id""", (rs, i) -> rs.getLong(1))
			.stream()
			.findFirst()
			.orElse(null);
		if (companyId == null) {
			log.error("GLOBAL_ADMIN bootstrap failed: no company exists yet.");
			return;
		}
		transaction.executeWithoutResult(status -> {
			List<Long> existingUser = jdbc.queryForList("SELECT id FROM user_account WHERE LOWER(email) = LOWER(?)",
					Long.class, bootstrapEmail);
			long userId;
			if (existingUser.isEmpty()) {
				userId = jdbc.queryForObject("""
						INSERT INTO user_account (email, display_name, company_id, preferred_language, is_active, is_demo)
						OUTPUT INSERTED.id VALUES (?, ?, ?, 'en', 1, 0)""", Long.class, bootstrapEmail.trim(),
						"Global Administrator", companyId);
			}
			else {
				userId = existingUser.get(0);
				jdbc.update("UPDATE user_account SET is_active = 1, updated_at = SYSUTCDATETIME() WHERE id = ?", userId);
			}
			credentials.savePasswordHash(userId, passwordEncoder.encode(bootstrapPassword), true);
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'GLOBAL_ADMIN'", userId);
			jdbc.update("INSERT INTO user_role (user_id, role, company_id, department_id) VALUES (?, 'GLOBAL_ADMIN', NULL, NULL)",
					userId);
			jdbc.update("""
					INSERT INTO identity_bootstrap (id, completed_at, actor_label)
					VALUES (1, SYSUTCDATETIME(), ?)""", "env:GLOBAL_ADMIN_BOOTSTRAP_PASSWORD");
		});
		log.warn(
				"GLOBAL_ADMIN bootstrap completed for {}. Clear GLOBAL_ADMIN_BOOTSTRAP_PASSWORD and change the password at first sign-in.",
				bootstrapEmail);
	}

}
