package com.epi.procurement.identity;

import java.security.SecureRandom;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

/**
 * DEMONSTRATION DATA ONLY. Creates the demo users of Sprints 1-2 (docs/product/sprint-1.md, sprint-2.md) when the
 * {@code demo} profile is active. Display names follow the Solution Guide v3.0 story.
 * <ul>
 * <li>No password is stored in the repository. Every start sets all demo passwords to {@code DEMO_USER_PASSWORD}
 * (stable, e.g. for QA), or to a random password printed once to the console when it is not set.</li>
 * <li>Accounts are flagged {@code is_demo}; they cannot sign in when the demo profile is off (S1DR-05).</li>
 * <li>Refuses to run on a database that contains non-demo companies.</li>
 * <li>Role grants are reset to the definition below on every start; the language preference is only set when an
 * account is first created, so a user's saved choice survives restarts.</li>
 * </ul>
 */
@Component
@Profile("demo")
// Before every other startup job (e.g. the approval job for Sprint 1 requests, which needs the demo approvers).
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class DemoUserSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

	static final List<DemoUser> USERS = List.of(
			new DemoUser("requester.tn@demo.local", "Sami Ben Ali (Demo)", "DEMO-TN-PLT", "DEMO-SFAX", "DEMO-MAINT", "fr",
					List.of(new Grant(Role.REQUESTER, true, false))),
			new DemoUser("assistant.tn@demo.local", "Amel Trabelsi (Demo)", "DEMO-TN-PLT", "DEMO-SFAX", "DEMO-MAINT", "fr",
					List.of(new Grant(Role.REQUESTER, true, false), new Grant(Role.ENTER_ON_BEHALF, true, false))),
			new DemoUser("manager.tn@demo.local", "Karim Haddad (Demo)", "DEMO-TN-PLT", "DEMO-SFAX", "DEMO-MAINT", "en",
					List.of(new Grant(Role.DEPARTMENT_MANAGER, true, true))),
			new DemoUser("manager2.tn@demo.local", "Hichem Gharbi (Demo)", "DEMO-TN-PLT", "DEMO-SFAX", "DEMO-MAINT", "fr",
					List.of(new Grant(Role.DEPARTMENT_MANAGER, true, true))),
			new DemoUser("budget.tn@demo.local", "Leila Mejri (Demo)", "DEMO-TN-PLT", "DEMO-SFAX", null, "ar",
					List.of(new Grant(Role.BUDGET_APPROVER, true, false))),
			new DemoUser("requester.fr@demo.local", "Claire Martin (Demo)", "DEMO-FR-PLT", "DEMO-LYON", "DEMO-MAINT", "fr",
					List.of(new Grant(Role.REQUESTER, true, false))),
			new DemoUser("manager.fr@demo.local", "Julien Moreau (Demo)", "DEMO-FR-PLT", "DEMO-LYON", "DEMO-MAINT", "fr",
					List.of(new Grant(Role.DEPARTMENT_MANAGER, true, true))),
			new DemoUser("receiver.tn@demo.local", "Omar Mansouri (Demo)", "DEMO-TN-PLT", "DEMO-SFAX", "DEMO-MAINT",
					"en", List.of(new Grant(Role.RECEIVER, true, false))),
			new DemoUser("buyer@demo.local", "Ines Jaziri (Demo)", "DEMO-CPA", "DEMO-RADES", "DEMO-PURCH", "en",
					List.of(new Grant(Role.BUYER, true, false))),
			new DemoUser("purchasing.manager@demo.local", "Mehdi Khelifi (Demo)", "DEMO-CPA", "DEMO-RADES", "DEMO-PURCH",
					"en", List.of(new Grant(Role.PURCHASING_MANAGER, true, false))),
			new DemoUser("ceo@demo.local", "Hedi Ferchichi (Demo)", "DEMO-HOLD", "DEMO-HQ", null, "en",
					List.of(new Grant(Role.GROUP_MANAGEMENT, false, false),
							new Grant(Role.MANAGEMENT_APPROVER, "DEMO-TN-PLT"),
							new Grant(Role.MANAGEMENT_APPROVER, "DEMO-FR-PLT"))),
			new DemoUser("admin@demo.local", "Platform Administrator (Demo)", "DEMO-HOLD", "DEMO-HQ", null, "en",
					List.of(new Grant(Role.ADMIN, false, false))));

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final CredentialStore credentials;

	private final PasswordEncoder passwordEncoder;

	private final String configuredPassword;

	DemoUserSeeder(DataSource dataSource, PlatformTransactionManager transactionManager, CredentialStore credentials,
			PasswordEncoder passwordEncoder,
			@Value("${app.demo.user-password:${DEMO_USER_PASSWORD:}}") String configuredPassword) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.transaction = new TransactionTemplate(transactionManager);
		this.credentials = credentials;
		this.passwordEncoder = passwordEncoder;
		this.configuredPassword = configuredPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer realCompanies = jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE code NOT LIKE 'DEMO-%'",
				Integer.class);
		if (realCompanies != null && realCompanies > 0) {
			log.error("Demo users NOT created: this database contains {} non-demo companies. The 'demo' profile must "
					+ "only be used with a dedicated demonstration database.", realCompanies);
			return;
		}
		Integer demoGroup = jdbc.queryForObject("SELECT COUNT(*) FROM organization_group WHERE code = 'DEMO-GRP'",
				Integer.class);
		if (demoGroup == null || demoGroup == 0) {
			log.warn("Demo users NOT created: the demo organisation is missing.");
			return;
		}
		boolean generated = !StringUtils.hasText(configuredPassword);
		String password = generated ? randomPassword() : configuredPassword;
		// One hash per account: same password, distinct salts (QA1-D-05).
		transaction.executeWithoutResult(tx -> USERS.forEach(user -> seed(user, passwordEncoder.encode(password))));
		if (generated) {
			// Printed to the console only (not through the logging system, which may write to files).
			System.out.println("""

					================================================================
					 DEMO USERS (demonstration data only)
					 Password for all %d demo accounts (*@demo.local): %s
					 Set DEMO_USER_PASSWORD to choose a stable password.
					================================================================
					""".formatted(USERS.size(), password));
		}
		else {
			log.info("Demo users ready: {} accounts, password taken from DEMO_USER_PASSWORD.", USERS.size());
		}
	}

	private void seed(DemoUser user, String passwordHash) {
		Long companyId = jdbc.queryForObject("SELECT id FROM company WHERE code = ?", Long.class, user.companyCode());
		Long siteId = jdbc.queryForObject("SELECT id FROM site WHERE company_id = ? AND code = ?", Long.class, companyId,
				user.siteCode());
		Long departmentId = user.departmentCode() == null ? null
				: jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = ?", Long.class,
						companyId, user.departmentCode());
		List<Long> existing = jdbc.queryForList("SELECT id FROM user_account WHERE email = ?", Long.class, user.email());
		long userId;
		if (existing.isEmpty()) {
			userId = jdbc.queryForObject("""
					INSERT INTO user_account (email, display_name, company_id, site_id, department_id, preferred_language, is_demo)
					OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, 1)""", Long.class, user.email(), user.displayName(),
					companyId, siteId, departmentId, user.language());
		}
		else {
			userId = existing.get(0);
			jdbc.update("""
					UPDATE user_account SET display_name = ?, company_id = ?, site_id = ?, department_id = ?, is_demo = 1,
					       is_active = 1, updated_at = SYSUTCDATETIME(), version = version + 1
					WHERE id = ?""", user.displayName(), companyId, siteId, departmentId, userId);
		}
		credentials.savePasswordHash(userId, passwordHash);
		jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
		for (Grant grant : user.grants()) {
			Long grantCompany = grant.companyCode() != null
					? jdbc.queryForObject("SELECT id FROM company WHERE code = ?", Long.class, grant.companyCode())
					: grant.companyScoped() ? companyId : null;
			jdbc.update("INSERT INTO user_role (user_id, role, company_id, department_id) VALUES (?, ?, ?, ?)", userId,
					grant.role().name(), grantCompany, grant.departmentScoped() ? departmentId : null);
		}
	}

	private static String randomPassword() {
		SecureRandom random = new SecureRandom();
		StringBuilder password = new StringBuilder("Demo-");
		for (int i = 0; i < 14; i++) {
			password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return password.toString();
	}

	record DemoUser(String email, String displayName, String companyCode, String siteCode, String departmentCode,
			String language, List<Grant> grants) {
	}

	/**
	 * A grant in the user's own company ({@code companyScoped}), or in another company of the group when
	 * {@code companyCode} is set (e.g. management approval for a subsidiary).
	 */
	record Grant(Role role, boolean companyScoped, boolean departmentScoped, String companyCode) {

		Grant(Role role, boolean companyScoped, boolean departmentScoped) {
			this(role, companyScoped, departmentScoped, null);
		}

		Grant(Role role, String companyCode) {
			this(role, true, false, companyCode);
		}

	}

}
