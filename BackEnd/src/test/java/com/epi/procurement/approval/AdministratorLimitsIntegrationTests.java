package com.epi.procurement.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sprint 1 hardening of administrator powers (audit S-4 to S-7): no account takeover through a chosen password,
 * no power over a peer administrator, no self-delegation of approvals, company scope on administrator listings,
 * and an audit event for every absence, approval rule and exchange rate change.
 */
class AdministratorLimitsIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// ---- S-4: passwords and peer administrators

	@Test
	void aCompanyAdministratorCannotChooseAnyonesPassword() {
		ApiClient admin = signedIn("admin@demo.local");
		long sami = userId("requester.tn@demo.local");
		String hashBefore = passwordHash(sami);

		assertThat(admin.post("/api/v1/admin/accounts/" + sami + "/temporary-password",
				"{\"temporaryPassword\":\"Chosen-By-Admin-2026\"}").expectStatus(403).code()).isEqualTo("forbidden");

		assertThat(passwordHash(sami)).isEqualTo(hashBefore);
		assertThat(auditCount("USER_ACCOUNT", Long.toString(sami), "TEMPORARY_PASSWORD_SET")).isZero();
		// The reset link remains available: the secret goes to the account owner only.
		admin.post("/api/v1/admin/accounts/" + sami + "/password-reset", null).expectStatus(202);
		signedIn("requester.tn@demo.local");
	}

	@Test
	void aGlobalAdministratorsTemporaryPasswordMustMeetThePolicyAndIsNeverTheirOwn() {
		ApiClient global = signedIn("global.admin@demo.local");
		// Created by the company administrator: nobody chooses a password for an account they created (finding 4).
		long target = createUserAs(signedIn("admin@demo.local"), "s1.temp");

		assertThat(global.post("/api/v1/admin/accounts/" + target + "/temporary-password",
				"{\"temporaryPassword\":\"aaaaaaaaaaaaaaaa\"}").expectStatus(400).code()).isEqualTo("password-too-weak");
		assertThat(global.post("/api/v1/admin/accounts/" + userId("global.admin@demo.local") + "/temporary-password",
				"{\"temporaryPassword\":\"Chosen-By-Admin-2026\"}").expectStatus(409).code())
			.isEqualTo("cannot-set-own-password");
		global.post("/api/v1/admin/accounts/" + target + "/temporary-password",
				"{\"temporaryPassword\":\"Chosen-By-Admin-2026\"}").expectStatus(204);
		assertThat(auditCount("USER_ACCOUNT", Long.toString(target), "TEMPORARY_PASSWORD_SET")).isEqualTo(1);
	}

	@Test
	void aCompanyAdministratorHasNoPowerOverAPeerAdministrator() {
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long grant = grantAdmin(global, julien, "DEMO-FR-PLT");
		try {
			ApiClient admin = signedIn("admin@demo.local");
			for (String action : new String[] { "password-reset", "revoke-sessions" }) {
				assertThat(admin.post("/api/v1/admin/accounts/" + julien + "/" + action, null).expectStatus(403).code())
					.as(action)
					.isEqualTo("peer-administrator");
			}
			assertThat(admin.post("/api/v1/admin/accounts/" + julien + "/active", "{\"active\":false}")
				.expectStatus(403)
				.code()).isEqualTo("peer-administrator");
			assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class, julien))
				.isTrue();
			// A global administrator still can.
			global.post("/api/v1/admin/accounts/" + julien + "/revoke-sessions", null).expectStatus(204);
		}
		finally {
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + grant + "/remove", null).expectStatus(200);
		}
	}

	// ---- S-5: delegation

	@Test
	void anAdministratorCannotRouteSomeoneElsesApprovalsToThemselves() {
		ApiClient admin = signedIn("admin@demo.local");
		long leila = userId("budget.tn@demo.local");
		int before = absenceCount(leila);

		ApiClient.Response refused = admin.post("/api/v1/admin/absences", "{\"userId\":" + leila
				+ ",\"adminOverride\":true,\"delegateUserId\":" + userId("admin@demo.local") + period())
			.expectStatus(422);
		assertThat(refused.body()).contains("delegate-is-administrator");
		// The same without the override flag.
		admin.post("/api/v1/admin/absences",
				"{\"userId\":" + leila + ",\"delegateUserId\":" + userId("admin@demo.local") + period())
			.expectStatus(422);

		assertThat(absenceCount(leila)).isEqualTo(before);
	}

	@Test
	void theOverrideDoesNotReachOutsideTheAbsentPersonsCompanies() {
		ApiClient admin = signedIn("admin@demo.local");
		long leila = userId("budget.tn@demo.local");
		// Claire belongs to the French plant only; Leila approves for the Tunisian plant.
		ApiClient.Response refused = admin.post("/api/v1/admin/absences", "{\"userId\":" + leila
				+ ",\"adminOverride\":true,\"delegateUserId\":" + userId("requester.fr@demo.local") + period())
			.expectStatus(422);
		assertThat(refused.body()).contains("delegate-not-eligible");
	}

	// ---- Review R-2: nobody below a global administrator decides their own rights

	@Test
	void aCompanyAdministratorCannotGiveThemselvesAnyRole() {
		ApiClient admin = signedIn("admin@demo.local");
		long self = userId("admin@demo.local");
		int before = roleCount(self);
		int versionBefore = credentialsVersion(self);
		for (String role : new String[] { "BUDGET_APPROVER", "DEPARTMENT_MANAGER", "MANAGEMENT_APPROVER", "BUYER",
				"PURCHASING_MANAGER", "RECEIVER", "REQUESTER" }) {
			assertThat(admin.post("/api/v1/admin/accounts/" + self + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + companyId("DEMO-TN-PLT") + ",\"departmentId\":null}")
				.expectStatus(403)
				.code()).as(role).isEqualTo("self-grant");
		}
		assertThat(roleCount(self)).isEqualTo(before);
		assertThat(credentialsVersion(self)).isEqualTo(versionBefore);
		assertThat((List<String>) admin.get("/api/v1/me").expectStatus(200).json("$.roles")).containsExactly("ADMIN");
	}

	@Test
	void theAdministratorWhoCreatesAnAccountCannotAloneMakeItAnApproverOrADelegate() {
		ApiClient admin = signedIn("admin@demo.local");
		ApiClient global = signedIn("global.admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long leila = userId("budget.tn@demo.local");

		// In the creation call itself: nothing is created.
		String refusedEmail = "s1.puppet." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		assertThat(admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Puppet","companyId":%d,"preferredLanguage":"en","active":true,
				 "roles":[{"role":"BUDGET_APPROVER","companyId":%d,"departmentId":null}]}
				""".formatted(refusedEmail, tn, tn)).expectStatus(403).code()).isEqualTo("four-eyes");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE email = ?", Integer.class, refusedEmail))
			.isZero();

		// Created without roles, then promoted by the same administrator.
		String email = "s1.puppet." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long puppet = admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Puppet","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, tn)).expectStatus(201).id();
		assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM user_account WHERE id = ?", Long.class, puppet))
			.isEqualTo(userId("admin@demo.local"));
		// F-1: a second administrator activates it; the creator still cannot empower it alone.
		global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":true}").expectStatus(200);
		// An ordinary role is the administrator's daily work and stays allowed.
		admin.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"REQUESTER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		for (String role : new String[] { "BUDGET_APPROVER", "DEPARTMENT_MANAGER", "MANAGEMENT_APPROVER" }) {
			assertThat(admin.post("/api/v1/admin/accounts/" + puppet + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(403).code())
				.as(role)
				.isEqualTo("four-eyes");
		}
		// Nor a delegate of the budget approver, with or without the override.
		int absences = absenceCount(leila);
		for (String override : new String[] { "true", "false" }) {
			assertThat(admin.post("/api/v1/admin/absences", "{\"userId\":" + leila + ",\"adminOverride\":" + override
					+ ",\"delegateUserId\":" + puppet + period()).expectStatus(422).body()).as(override)
				.contains("delegate-created-by-administrator");
		}
		assertThat(absenceCount(leila)).isEqualTo(absences);

		// A second person can: the global administrator gives the approval role.
		global.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"BUDGET_APPROVER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		// The creator still cannot name it delegate, although it now shares the approver's role.
		assertThat(admin.post("/api/v1/admin/absences",
				"{\"userId\":" + leila + ",\"delegateUserId\":" + puppet + period()).expectStatus(422).body())
			.contains("delegate-created-by-administrator");
		// The legitimate workflow is intact: the global administrator records that absence.
		long absence = global.post("/api/v1/admin/absences",
				"{\"userId\":" + leila + ",\"delegateUserId\":" + puppet + period()).expectStatus(201).id();
		global.delete("/api/v1/admin/absences/" + absence).expectStatus(204);

		// The rule does not expire (RR-3): a year later the creator still cannot give it an approval role.
		clock.advance(Duration.ofDays(365));
		ApiClient later = signedIn("admin@demo.local");
		assertThat(later.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"DEPARTMENT_MANAGER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(403).code())
			.isEqualTo("four-eyes");
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}")
			.expectStatus(200);
	}

	// ---- RR-3: four eyes for every sensitive role, every administrator, every account

	@Test
	void theCreatorOfAnAccountGivesItNoBuyingOrderingOrReceivingRoleEither() {
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long cpa = companyId("DEMO-CPA");
		// In the creation call itself.
		for (String role : new String[] { "BUYER", "RECEIVER", "PURCHASING_MANAGER" }) {
			String email = "s1.rr3." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
			assertThat(admin.post("/api/v1/admin/accounts", """
					{"email":"%s","displayName":"RR3","companyId":%d,"preferredLanguage":"en","active":true,
					 "roles":[{"role":"%s","companyId":%d,"departmentId":null}]}
					""".formatted(email, tn, role, role.equals("RECEIVER") ? tn : cpa)).expectStatus(403).code())
				.as(role)
				.isEqualTo("four-eyes");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE email = ?", Integer.class, email))
				.isZero();
		}
		// Afterwards, on its own.
		long puppet = createUserAs(admin, "s1.rr3");
		for (String role : new String[] { "BUYER", "RECEIVER", "PURCHASING_MANAGER" }) {
			long company = role.equals("RECEIVER") ? tn : cpa;
			assertThat(admin.post("/api/v1/admin/accounts/" + puppet + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + company + ",\"departmentId\":null}").expectStatus(403)
				.code()).as(role).isEqualTo("four-eyes");
		}
		// Ordinary roles stay the creator's daily work.
		admin.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"REQUESTER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		assertThat(jdbc.queryForList("SELECT role FROM user_role WHERE user_id = ?", String.class, puppet))
			.containsExactly("REQUESTER");

		// The escalation path for a company with a single administrator: a global administrator grants.
		ApiClient global = signedIn("global.admin@demo.local");
		global.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"RECEIVER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}").expectStatus(200);
	}

	@Test
	void aGlobalAdministratorIsBoundByTheSameRule() {
		ApiClient global = signedIn("global.admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long globalId = userId("global.admin@demo.local");
		long puppet = createUserAs(global, "s1.rr3.global");
		for (String role : new String[] { "BUDGET_APPROVER", "BUYER", "RECEIVER" }) {
			long company = role.equals("BUYER") ? companyId("DEMO-CPA") : tn;
			assertThat(global.post("/api/v1/admin/accounts/" + puppet + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + company + ",\"departmentId\":null}").expectStatus(403)
				.code()).as(role).isEqualTo("four-eyes");
			// Nor to themselves.
			assertThat(global.post("/api/v1/admin/accounts/" + globalId + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + company + ",\"departmentId\":null}").expectStatus(403)
				.code()).as("self " + role).isEqualTo("self-grant");
		}
		// Nor name it approver in a rule, nor delegate of an approver.
		assertThat(global.post("/api/v1/admin/approval-rules", namedRule(tn, puppet)).expectStatus(422).body())
			.contains("approver-created-by-administrator");
		assertThat(global.post("/api/v1/admin/approval-rules", namedRule(tn, globalId)).expectStatus(422).body())
			.contains("approver-is-administrator");
		assertThat(global.post("/api/v1/admin/absences", "{\"userId\":" + userId("budget.tn@demo.local")
				+ ",\"adminOverride\":true,\"delegateUserId\":" + puppet + period()).expectStatus(422).body())
			.contains("delegate-created-by-administrator");
		// Another administrator can: the company administrator of the plant.
		signedIn("admin@demo.local").post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"BUDGET_APPROVER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}").expectStatus(200);
	}

	@Test
	void accountsCreatedBeforeTheCreatorWasStoredAreRecognisedFromTheAuditTrail() {
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long puppet = createUserAs(admin, "s1.rr3.legacy");
		// As for an account created before migration V24: no creator on the row, only the USER_CREATED event.
		jdbc.update("UPDATE user_account SET created_by_user_id = NULL WHERE id = ?", puppet);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_type = 'USER_ACCOUNT' "
				+ "AND entity_id = ? AND action = 'USER_CREATED' AND actor_user_id = ?", Integer.class,
				Long.toString(puppet), userId("admin@demo.local"))).isEqualTo(1);
		assertThat(admin.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"BUDGET_APPROVER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(403).code())
			.isEqualTo("four-eyes");
		assertThat(admin.post("/api/v1/admin/absences", "{\"userId\":" + userId("budget.tn@demo.local")
				+ ",\"adminOverride\":true,\"delegateUserId\":" + puppet + period()).expectStatus(422).body())
			.contains("delegate-created-by-administrator");
		// Accounts with no recorded creator at all (demo seed, first administrator) are ordinary colleagues.
		long seeded = userId("manager2.tn@demo.local");
		assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM user_account WHERE id = ?", Long.class, seeded))
			.isNull();
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}")
			.expectStatus(200);
	}

	@Test
	void theCreatorCannotNameTheirAccountAsDelegateEvenInSelfService() {
		// An administrator who is also an approver records their own absence: the account they created, even once
		// another administrator has made it an approver, is not an independent delegate for them.
		ApiClient global = signedIn("global.admin@demo.local");
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long adminId = userId("admin@demo.local");
		long puppet = createUserAs(admin, "s1.rr3.self");
		global.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"BUDGET_APPROVER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		global.post("/api/v1/admin/accounts/" + adminId + "/roles",
				"{\"role\":\"BUDGET_APPROVER\",\"companyId\":" + tn + ",\"departmentId\":null}").expectStatus(200);
		long grant = jdbc.queryForObject("SELECT id FROM user_role WHERE user_id = ? AND role = 'BUDGET_APPROVER' "
				+ "AND company_id = ?", Long.class, adminId, tn);
		try {
			ApiClient adminAgain = signedIn("admin@demo.local");
			assertThat(adminAgain.post("/api/v1/me/absences", "{\"delegateUserId\":" + puppet + period())
				.expectStatus(422)
				.body()).contains("delegate-created-by-administrator");
		}
		finally {
			global.post("/api/v1/admin/accounts/" + adminId + "/roles/" + grant + "/remove", null).expectStatus(200);
			global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}").expectStatus(200);
		}
	}

	/** Creates an account as {@code creator}; the other demo administrator activates it (F-1: two people). */
	// ---- Fourth review (F-1, F-2): creation chain, first credential, audited grants

	@Test
	void aTemporaryPasswordIsNeverTheFirstCredentialNorChosenByTheCreator() {
		ApiClient global = signedIn("global.admin@demo.local");
		String email = "s1.first." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long account = global.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"First Credential","companyId":%d,"preferredLanguage":"en","roles":[]}
				""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		String hash = passwordHash(account);
		String body = "{\"temporaryPassword\":\"Chosen-By-Global-2026\"}";
		assertThat(global.post("/api/v1/admin/accounts/" + account + "/temporary-password", body).expectStatus(403).code())
			.as("inactive").isEqualTo("use-reset-link");
		signedIn("admin@demo.local").post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":true}")
			.expectStatus(200);
		assertThat(global.post("/api/v1/admin/accounts/" + account + "/temporary-password", body).expectStatus(403).code())
			.as("creator").isEqualTo("use-reset-link");
		assertThat(passwordHash(account)).isEqualTo(hash);
		assertThat(auditCount("USER_ACCOUNT", Long.toString(account), "TEMPORARY_PASSWORD_SET")).isZero();
	}

	@Test
	void theWholeCreationChainCountsAsTheCreator() {
		// The global administrator created B; B created C (set directly: B holds no administration role here).
		long b = createUserAs(signedIn("global.admin@demo.local"), "s1.chain.b");
		String email = "s1.chain.c." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long c = signedIn("admin@demo.local").post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Chain C","companyId":%d,"preferredLanguage":"en","roles":[]}
				""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		jdbc.update("UPDATE user_account SET created_by_user_id = ? WHERE id = ?", b, c);

		ApiClient global = signedIn("global.admin@demo.local");
		assertThat(global.post("/api/v1/admin/accounts/" + c + "/active", "{\"active\":true}").expectStatus(403).code())
			.isEqualTo("four-eyes");
		// Someone outside the chain activates it; the global administrator still cannot empower it.
		signedIn("admin@demo.local").post("/api/v1/admin/accounts/" + c + "/active", "{\"active\":true}").expectStatus(200);
		for (String role : new String[] { "BUYER", "ADMIN" }) {
			assertThat(global.post("/api/v1/admin/accounts/" + c + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + companyId("DEMO-TN-PLT") + ",\"departmentId\":null}")
				.expectStatus(403)
				.code()).as(role).isEqualTo("four-eyes");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id = ?", Integer.class, c)).isZero();
	}

	@Test
	void rolesGivenAtCreationAreAudited() {
		String email = "s1.audit." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long tn = companyId("DEMO-TN-PLT");
		long account = signedIn("admin@demo.local").post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Audited","companyId":%d,"preferredLanguage":"en",
				 "roles":[{"role":"REQUESTER","companyId":%d,"departmentId":null}]}
				""".formatted(email, tn, tn)).expectStatus(201).id();
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_event WHERE entity_type = 'USER_ROLE' AND action = 'ROLE_GRANTED'
				AND entity_id IN (SELECT CAST(id AS NVARCHAR(40)) FROM user_role WHERE user_id = ?)""", Integer.class, account))
			.isEqualTo(1);
	}

	private long createUserAs(ApiClient creator, String prefix) {
		String email = prefix + "." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long id = creator.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"RR3 Probe","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		activateByTheOtherAdministrator(id);
		return id;
	}

	private void activateByTheOtherAdministrator(long account) {
		long creator = jdbc.queryForObject("SELECT created_by_user_id FROM user_account WHERE id = ?", Long.class, account);
		String other = creator == userId("global.admin@demo.local") ? "admin@demo.local" : "global.admin@demo.local";
		signedIn(other).post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":true}").expectStatus(200);
	}

	// ---- Review R-1: who may be a delegate

	@Test
	void aSupplierContactOrAnOutsiderWithOnlyAGroupWideRoleIsNeverADelegate() {
		long leila = userId("budget.tn@demo.local");
		long contact = userId("contact.skf@demo.local");
		int before = absenceCount(leila);
		// Whoever asks, and with the override: an approval is an internal decision.
		for (String who : new String[] { "admin@demo.local", "global.admin@demo.local" }) {
			assertThat(signedIn(who).post("/api/v1/admin/absences", "{\"userId\":" + leila
					+ ",\"adminOverride\":true,\"delegateUserId\":" + contact + period()).expectStatus(422).body()).as(who)
				.contains("delegate-not-eligible");
		}
		// A role without a company is not membership of the absent person's company.
		assertThat(signedIn("admin@demo.local").post("/api/v1/admin/absences", "{\"userId\":" + leila
				+ ",\"adminOverride\":true,\"delegateUserId\":" + userId("global.admin@demo.local") + period())
			.expectStatus(422)
			.body()).contains("delegate-not-eligible");
		assertThat(absenceCount(leila)).isEqualTo(before);

		// Legitimate overrides still work: a colleague of the same plant, and the CEO who approves for that plant.
		ApiClient admin = signedIn("admin@demo.local");
		for (String delegate : new String[] { "requester.tn@demo.local", "ceo@demo.local" }) {
			long absence = admin.post("/api/v1/admin/absences", "{\"userId\":" + leila
					+ ",\"adminOverride\":true,\"delegateUserId\":" + userId(delegate) + period()).expectStatus(201).id();
			admin.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
		}
	}

	// ---- Re-review RR-1, RR-2, RR-5: the same rules on the other routes to an approval

	@Test
	void anAdministratorCannotWriteThemselvesOrTheirOwnCreationIntoAnApprovalRule() {
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		int rules = jdbc.queryForObject("SELECT COUNT(*) FROM approval_rule WHERE company_id = ?", Integer.class, tn);
		String email = "s1.rule." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long puppet = admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Rule Puppet","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, tn)).expectStatus(201).id();
		// F-1: activated by a second administrator, so only the creator rule can refuse it below.
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":true}")
			.expectStatus(200);

		assertThat(admin.post("/api/v1/admin/approval-rules", namedRule(tn, userId("admin@demo.local")))
			.expectStatus(422)
			.body()).contains("approver-is-administrator");
		assertThat(admin.post("/api/v1/admin/approval-rules", namedRule(tn, puppet)).expectStatus(422).body())
			.contains("approver-created-by-administrator");
		// A supplier contact is never an approver, whoever writes the rule.
		for (String who : new String[] { "admin@demo.local", "global.admin@demo.local" }) {
			assertThat(signedIn(who).post("/api/v1/admin/approval-rules", namedRule(tn, userId("contact.skf@demo.local")))
				.expectStatus(422)
				.body()).as(who).contains("approverUserId");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_rule WHERE company_id = ?", Integer.class, tn))
			.isEqualTo(rules);

		// Legitimate: a colleague as named approver; and the global administrator may name the new account.
		long byAdmin = admin.post("/api/v1/admin/approval-rules", namedRule(tn, userId("manager2.tn@demo.local")))
			.expectStatus(201)
			.id();
		long byGlobal = signedIn("global.admin@demo.local").post("/api/v1/admin/approval-rules", namedRule(tn, puppet))
			.expectStatus(201)
			.id();
		// Editing the draft cannot bring the administrator in afterwards.
		assertThat(admin.put("/api/v1/admin/approval-rules/" + byAdmin, namedRule(tn, userId("admin@demo.local")))
			.expectStatus(422)
			.body()).contains("approver-is-administrator");
		assertThat(byGlobal).isNotEqualTo(byAdmin);
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}")
			.expectStatus(200);
	}

	@Test
	void aSupplierContactAccountNeverReceivesAnInternalRole() {
		long contact = userId("contact.skf@demo.local");
		int before = roleCount(contact);
		for (String who : new String[] { "admin@demo.local", "global.admin@demo.local" }) {
			for (String role : new String[] { "MANAGEMENT_APPROVER", "BUDGET_APPROVER", "BUYER", "REQUESTER" }) {
				assertThat(signedIn(who).post("/api/v1/admin/accounts/" + contact + "/roles",
						"{\"role\":\"" + role + "\",\"companyId\":" + companyId("DEMO-CPA") + ",\"departmentId\":null}")
					.status()).as(who + " " + role).isIn(400, 403, 404);
			}
		}
		assertThat(roleCount(contact)).isEqualTo(before);
	}

	/** A draft rule that no request can match (amount band far above anything used), with one named approver. */
	private String namedRule(long company, long approver) {
		return JSON.writeValueAsString(Map.of("companyId", company, "name", "Named probe (test)", "purchaseType",
				"INTERNATIONAL", "minAmount", 910000, "maxAmount", 910001, "stages", List.of(Map.of("name", "Named",
						"approverType", "NAMED_USER", "approverUserId", approver, "policy", "ANY", "slaHours", 8))));
	}

	// ---- Re-review RR-7, RR-8: privileged cross-company

	@Test
	void anAdministratorCannotTouchAnAccountOfAnotherCompanyByGivingItARole() {
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long fr = companyId("DEMO-FR-PLT");
		long sami = userId("requester.tn@demo.local");
		long adminGrant = grantAdmin(global, julien, "DEMO-FR-PLT");
		try {
			ApiClient samiClient = signedIn("requester.tn@demo.local");
			int roles = roleCount(sami);
			int version = credentialsVersion(sami);
			ApiClient frAdmin = signedIn("manager.fr@demo.local");
			// Same answer as for an account that does not exist; nothing about Sami is returned.
			ApiClient.Response refused = frAdmin.post("/api/v1/admin/accounts/" + sami + "/roles",
					"{\"role\":\"REQUESTER\",\"companyId\":" + fr + ",\"departmentId\":null}");
			refused.expectStatus(404);
			assertThat(refused.body()).doesNotContain("requester.tn@demo.local");
			frAdmin.post("/api/v1/admin/accounts/987654321/roles",
					"{\"role\":\"REQUESTER\",\"companyId\":" + fr + ",\"departmentId\":null}").expectStatus(404);
			assertThat(roleCount(sami)).isEqualTo(roles);
			// Sami was not signed out.
			assertThat(credentialsVersion(sami)).isEqualTo(version);
			samiClient.get("/api/v1/me").expectStatus(200);
		}
		finally {
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(200);
		}
	}

	@Autowired
	ApproverResolver resolver;

	@Autowired
	com.epi.procurement.request.RequestWorkflow workflow;

	@Test
	void anOverrideDelegateReceivesOnlyTheApprovalsOfTheirOwnCompany() {
		// The CEO approves for both plants and belongs to the holding. An override names a holding employee who has
		// no role in either plant (the global administrator: home company the holding, only a group-wide role).
		// The absence is accepted, because the delegate shares the CEO's home company, but no plant's approval may
		// be routed to them. The absence is recorded by the company administrator of the holding.
		ApiClient global = signedIn("admin@demo.local");
		long ceo = userId("ceo@demo.local");
		long holdingDelegate = userId("global.admin@demo.local");
		Instant now = clock.instant();
		long absence = global.post("/api/v1/admin/absences", "{\"userId\":" + ceo + ",\"adminOverride\":true,"
				+ "\"delegateUserId\":" + holdingDelegate + ",\"startsAt\":\"" + now.minusSeconds(60) + "\",\"endsAt\":\""
				+ now.plus(Duration.ofHours(2)) + "\"}").expectStatus(201).id();
		try {
			ApiClient sami = signedIn("requester.tn@demo.local");
			Map<String, Object> body = new java.util.LinkedHashMap<>();
			body.put("title", "Override routing (test)");
			body.put("categoryId", jdbc.queryForObject("SELECT c.id FROM request_category c JOIN organization_group g "
					+ "ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'", Long.class));
			body.put("priority", "NORMAL");
			body.put("purchaseType", "LOCAL");
			body.put("currency", "TND");
			body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = "
					+ "'DEMO-MAINT'", Long.class, companyId("DEMO-TN-PLT")));
			body.put("requiredDate", LocalDate.now(clock).plusDays(30).toString());
			body.put("lines", List.of(Map.of("description", "Item", "quantity", 1, "unit", "PCE", "estimatedUnitPrice", 10)));
			long request = sami.post("/api/v1/requests", JSON.writeValueAsString(body)).expectStatus(201).id();
			var snapshot = workflow.findAll(List.of(request)).get(request);

			ApproverResolver.Routing routing = resolver.route(ceo, "MANAGEMENT_APPROVER", null, snapshot, clock.instant());
			assertThat(routing.assignee()).as("the task stays with the CEO").isEqualTo(ceo);
			assertThat(routing.note()).isEqualTo("ABSENCE_UNRESOLVED");

			// Control: a delegate who does belong to the plant receives it.
			global.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
			absence = global.post("/api/v1/admin/absences", "{\"userId\":" + ceo + ",\"adminOverride\":true,"
					+ "\"delegateUserId\":" + userId("manager2.tn@demo.local") + ",\"startsAt\":\"" + now.minusSeconds(60)
					+ "\",\"endsAt\":\"" + now.plus(Duration.ofHours(2)) + "\"}").expectStatus(201).id();
			ApproverResolver.Routing routed = resolver.route(ceo, "MANAGEMENT_APPROVER", null, snapshot, clock.instant());
			assertThat(routed.assignee()).isEqualTo(userId("manager2.tn@demo.local"));
			assertThat(routed.note()).isEqualTo("ABSENCE_DELEGATE");
		}
		finally {
			global.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
		}
	}

	// ---- Independent review of RR-3: F-3, F-4, F-6 to F-10 and the legitimate second-administrator path

	@Test
	void aSecondAdministratorGrantsBuyingOrderingAndReceivingRoles() {
		ApiClient admin = signedIn("admin@demo.local");
		ApiClient global = signedIn("global.admin@demo.local");
		long puppet = createUserAs(admin, "s1.f.second");
		long cpa = companyId("DEMO-CPA");
		for (String role : new String[] { "BUYER", "PURCHASING_MANAGER" }) {
			global.post("/api/v1/admin/accounts/" + puppet + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + cpa + ",\"departmentId\":null}").expectStatus(200);
		}
		global.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"RECEIVER\",\"companyId\":" + companyId("DEMO-TN-PLT") + ",\"departmentId\":null}")
			.expectStatus(200);
		assertThat(jdbc.queryForList("SELECT role FROM user_role WHERE user_id = ? ORDER BY role", String.class, puppet))
			.containsExactly("BUYER", "PURCHASING_MANAGER", "RECEIVER");
		global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}").expectStatus(200);
	}

	@Test
	void noChosenPasswordForAnAccountThatApprovesBuysReceivesOrAdministers() {
		ApiClient global = signedIn("global.admin@demo.local");
		String hash = jdbc.queryForObject("SELECT password_hash FROM user_credential WHERE user_id = ?", String.class,
				userId("budget.tn@demo.local"));
		for (String email : new String[] { "budget.tn@demo.local", "buyer@demo.local", "receiver.tn@demo.local",
				"admin@demo.local" }) {
			assertThat(global.post("/api/v1/admin/accounts/" + userId(email) + "/temporary-password",
					"{\"temporaryPassword\":\"Chosen-By-Global-2026\"}").expectStatus(403).code()).as(email)
				.isEqualTo("use-reset-link");
		}
		assertThat(jdbc.queryForObject("SELECT password_hash FROM user_credential WHERE user_id = ?", String.class,
				userId("budget.tn@demo.local"))).isEqualTo(hash);
		// The reset link remains, for every account.
		global.post("/api/v1/admin/accounts/" + userId("budget.tn@demo.local") + "/password-reset", null)
			.expectStatus(202);
	}

	@Test
	void aSupplierContactStaysASupplierContactAndRecordsWhoInvitedIt() {
		ApiClient global = signedIn("global.admin@demo.local");
		long contact = userId("contact.skf@demo.local");
		long grant = jdbc.queryForObject("SELECT id FROM user_role WHERE user_id = ? AND role = 'SUPPLIER_CONTACT'",
				Long.class, contact);
		assertThat(global.post("/api/v1/admin/accounts/" + contact + "/roles/" + grant + "/remove", null)
			.expectStatus(409)
			.code()).isEqualTo("supplier-contact-role");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE id = ?", Integer.class, grant)).isEqualTo(1);

		// A new invitation records the buyer who sent it as the account's creator.
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		String email = "s1.contact." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@supplier.example";
		signedIn("buyer@demo.local").post("/api/v1/messaging/suppliers/" + skf + "/contacts",
				"{\"email\":\"" + email + "\",\"displayName\":\"Invited Contact\"}").expectStatus(201);
		assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM user_account WHERE email = ?", Long.class, email))
			.isEqualTo(userId("buyer@demo.local"));
	}

	@Test
	void aNamedApproverWorksForTheRulesCompany() {
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		// Claire works for the French plant only.
		assertThat(admin.post("/api/v1/admin/approval-rules", namedRule(tn, userId("requester.fr@demo.local")))
			.expectStatus(422)
			.body()).contains("not-in-company");
		// The CEO, at the holding, approves for the Tunisian plant through a role granted there.
		admin.post("/api/v1/admin/approval-rules", namedRule(tn, userId("ceo@demo.local"))).expectStatus(201);
	}

	@Test
	void aRoleOfAnotherCompanysAccountCannotEvenBeAskedAbout() {
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long adminGrant = grantAdmin(global, julien, "DEMO-FR-PLT");
		try {
			long karim = userId("manager.tn@demo.local");
			long karimGrant = jdbc.queryForObject("SELECT TOP 1 id FROM user_role WHERE user_id = ?", Long.class, karim);
			ApiClient frAdmin = signedIn("manager.fr@demo.local");
			// The same answer as for an account that does not exist (F-7).
			frAdmin.post("/api/v1/admin/accounts/" + karim + "/roles/" + karimGrant + "/remove", null).expectStatus(404);
			// Also an administrator account outside the French plant (the global administrator, home: the holding).
			frAdmin.post("/api/v1/admin/accounts/" + userId("global.admin@demo.local") + "/roles/1/remove", null)
				.expectStatus(404);
			// An administrator who also administers the French plant is visible: a peer, refused as such.
			assertThat(frAdmin.post("/api/v1/admin/accounts/" + userId("admin@demo.local") + "/roles/1/remove", null)
				.expectStatus(403)
				.code()).isEqualTo("peer-administrator");
			// Listings show no supplier contact and no global administrator of the group (F-10).
			List<String> emails = frAdmin.get("/api/v1/admin/users").expectStatus(200).json("$[*].email");
			assertThat(emails).doesNotContain("contact.skf@demo.local", "global.admin@demo.local")
				.contains("requester.fr@demo.local", "ceo@demo.local");
		}
		finally {
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(200);
		}
	}

	@Test
	void theCreatorCannotReactivateAnAccountHoldingASensitiveRole() {
		ApiClient admin = signedIn("admin@demo.local");
		ApiClient global = signedIn("global.admin@demo.local");
		long puppet = createUserAs(admin, "s1.f.react");
		global.post("/api/v1/admin/accounts/" + puppet + "/roles",
				"{\"role\":\"RECEIVER\",\"companyId\":" + companyId("DEMO-TN-PLT") + ",\"departmentId\":null}")
			.expectStatus(200);
		global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}").expectStatus(200);
		assertThat(admin.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":true}").expectStatus(403).code())
			.isEqualTo("four-eyes");
		assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class, puppet)).isFalse();
		// Another administrator can; the creator can still deactivate.
		global.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":true}").expectStatus(200);
		admin.post("/api/v1/admin/accounts/" + puppet + "/active", "{\"active\":false}").expectStatus(200);
	}

	@Test
	void anOverrideRecordedByAnAdministratorOfAnotherCompanyDoesNotRouteThisCompanysApprovals() {
		// Review F-9: an override recorded by an administrator of the French plant only, for the CEO who also
		// approves for the Tunisian plant, must not move Tunisian approvals, even to a Tunisian employee.
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long ceo = userId("ceo@demo.local");
		long sami = userId("requester.tn@demo.local");
		long adminGrant = grantAdmin(global, julien, "DEMO-FR-PLT");
		Instant now = clock.instant();
		Long absence = null;
		try {
			absence = jdbc.queryForObject("""
					INSERT INTO user_absence (user_id, delegate_user_id, starts_at, ends_at, reason, admin_override,
					                          created_by, created_at)
					OUTPUT INSERTED.id VALUES (?, ?, ?, ?, NULL, 1, ?, ?)""", Long.class, ceo, userId("manager2.tn@demo.local"),
					com.epi.procurement.platform.persistence.UtcTimes.db(now.minusSeconds(60)),
					com.epi.procurement.platform.persistence.UtcTimes.db(now.plus(Duration.ofHours(2))), julien,
					com.epi.procurement.platform.persistence.UtcTimes.db(now));
			Map<String, Object> body = new java.util.LinkedHashMap<>();
			body.put("title", "Override scope (test)");
			body.put("categoryId", jdbc.queryForObject("SELECT c.id FROM request_category c JOIN organization_group g "
					+ "ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'", Long.class));
			body.put("priority", "NORMAL");
			body.put("purchaseType", "LOCAL");
			body.put("currency", "TND");
			body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = "
					+ "'DEMO-MAINT'", Long.class, companyId("DEMO-TN-PLT")));
			body.put("requiredDate", LocalDate.now(clock).plusDays(30).toString());
			body.put("lines", List.of(Map.of("description", "Item", "quantity", 1, "unit", "PCE", "estimatedUnitPrice", 10)));
			long request = signedIn("requester.tn@demo.local").post("/api/v1/requests", JSON.writeValueAsString(body))
				.expectStatus(201)
				.id();
			var snapshot = workflow.findAll(List.of(request)).get(request);
			ApproverResolver.Routing routing = resolver.route(ceo, "MANAGEMENT_APPROVER", null, snapshot, now);
			assertThat(routing.assignee()).isEqualTo(ceo);
			assertThat(routing.note()).isEqualTo("ABSENCE_UNRESOLVED");
			assertThat(sami).isPositive();
		}
		finally {
			if (absence != null) {
				jdbc.update("UPDATE user_absence SET cancelled_at = ? WHERE id = ?",
						com.epi.procurement.platform.persistence.UtcTimes.db(now), absence);
			}
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(200);
		}
	}

	// ---- F-1: two-person activation of new accounts

	@Test
	void aNewAccountStaysInactiveUntilAnotherAdministratorActivatesIt() {
		ApiClient admin = signedIn("admin@demo.local");
		String email = "s1.f1." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		// "active": true in the request is ignored.
		long account = admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"F1 Probe","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class, account)).isFalse();
		// No set-up link yet: its owner cannot act before the second person.
		assertThat(setupMails(account)).isZero();

		// The creator cannot activate it.
		assertThat(admin.post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":true}").expectStatus(403).code())
			.isEqualTo("four-eyes");
		assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class, account)).isFalse();

		// Another administrator does; the owner then receives the set-up link; the history is in the audit trail.
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":true}")
			.expectStatus(200);
		assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class, account)).isTrue();
		assertThat(setupMails(account)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_type = 'USER_ACCOUNT' AND entity_id = ? "
				+ "AND action IN ('USER_CREATED', 'USER_ACTIVATED', 'PASSWORD_SETUP_ISSUED') ORDER BY chain_seq, id",
				String.class, Long.toString(account))).containsExactly("USER_CREATED", "USER_ACTIVATED", "PASSWORD_SETUP_ISSUED");
		assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_event WHERE entity_type = 'USER_ACCOUNT' AND "
				+ "entity_id = ? AND action = 'USER_ACTIVATED'", Long.class, Long.toString(account)))
			.isEqualTo(userId("global.admin@demo.local"));
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":false}")
			.expectStatus(200);
	}

	@Test
	void anAdministratorCreatedByTheSamePersonCannotActivateTheirAccounts() {
		// Indirect self-approval: if X created administrator Y, Y does not activate X's accounts.
		ApiClient global = signedIn("global.admin@demo.local");
		long globalId = userId("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long adminGrant = grantAdmin(global, julien, "DEMO-FR-PLT");
		Long previousCreator = jdbc.queryForObject("SELECT created_by_user_id FROM user_account WHERE id = ?", Long.class,
				julien);
		// Fixture: record the global administrator as the creator of Julien's account.
		jdbc.update("UPDATE user_account SET created_by_user_id = ? WHERE id = ?", globalId, julien);
		try {
			String email = "s1.f1.ind." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
			long account = global.post("/api/v1/admin/accounts", """
					{"email":"%s","displayName":"F1 Indirect","companyId":%d,"preferredLanguage":"en","roles":[]}
					""".formatted(email, companyId("DEMO-FR-PLT"))).expectStatus(201).id();
			assertThat(signedIn("manager.fr@demo.local").post("/api/v1/admin/accounts/" + account + "/active",
					"{\"active\":true}").expectStatus(403).code()).isEqualTo("four-eyes");
			// An administrator not linked to the creator can.
			signedIn("admin@demo.local").post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":true}")
				.expectStatus(200);
			global.post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":false}").expectStatus(200);
		}
		finally {
			jdbc.update("UPDATE user_account SET created_by_user_id = ? WHERE id = ?", previousCreator, julien);
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(200);
		}
	}

	@Test
	void anAdministratorOfAnotherCompanyCannotActivateAnAccount() {
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long adminGrant = grantAdmin(global, julien, "DEMO-FR-PLT");
		try {
			String email = "s1.f1.x." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
			long account = signedIn("admin@demo.local").post("/api/v1/admin/accounts", """
					{"email":"%s","displayName":"F1 Cross","companyId":%d,"preferredLanguage":"en","roles":[]}
					""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
			signedIn("manager.fr@demo.local").post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":true}")
				.expectStatus(404);
			assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE id = ?", Boolean.class, account))
				.isFalse();
		}
		finally {
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(200);
		}
	}

	@Test
	void existingAccountsAreUnaffected() {
		for (String email : new String[] { "requester.tn@demo.local", "buyer@demo.local", "admin@demo.local",
				"global.admin@demo.local" }) {
			assertThat(jdbc.queryForObject("SELECT is_active FROM user_account WHERE email = ?", Boolean.class, email))
				.as(email)
				.isTrue();
			signedIn(email).get("/api/v1/me").expectStatus(200);
		}
	}

	// ---- F-2: administration roles never from the account's creator

	@Test
	void theCreatorOfAnAccountNeverMakesItAnAdministrator() {
		ApiClient global = signedIn("global.admin@demo.local");
		long globalId = userId("global.admin@demo.local");
		long account = createUser(global, "s1.f2");
		for (String role : new String[] { "ADMIN", "GLOBAL_ADMIN" }) {
			String company = role.equals("ADMIN") ? Long.toString(companyId("DEMO-TN-PLT")) : "null";
			assertThat(global.post("/api/v1/admin/accounts/" + account + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + company + ",\"departmentId\":null}").expectStatus(403)
				.code()).as(role).isEqualTo("four-eyes");
			// Nor to themselves.
			assertThat(global.post("/api/v1/admin/accounts/" + globalId + "/roles",
					"{\"role\":\"" + role + "\",\"companyId\":" + company + ",\"departmentId\":null}").expectStatus(403)
				.code()).as("self " + role).isEqualTo("self-grant");
		}
		// A company administrator can never grant administration roles at all.
		ApiClient admin = signedIn("admin@demo.local");
		admin.post("/api/v1/admin/accounts/" + account + "/roles",
				"{\"role\":\"ADMIN\",\"companyId\":" + companyId("DEMO-TN-PLT") + ",\"departmentId\":null}")
			.expectStatus(403);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role IN ('ADMIN', 'GLOBAL_ADMIN')",
				Integer.class, account)).isZero();
		global.post("/api/v1/admin/accounts/" + account + "/active", "{\"active\":false}").expectStatus(200);
	}

	private int setupMails(long account) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE recipient_user_id = ? AND kind = "
				+ "'PASSWORD_SETUP'", Integer.class, account);
	}

	// ---- Review R-3: roles of a peer administrator

	@Test
	void aCompanyAdministratorCannotChangeTheRolesOfAPeerAdministrator() {
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long fr = companyId("DEMO-FR-PLT");
		long adminGrant = grantAdmin(global, julien, "DEMO-FR-PLT");
		try {
			ApiClient peer = signedIn("manager.fr@demo.local");
			int roles = roleCount(julien);
			int version = credentialsVersion(julien);
			long managerGrant = jdbc.queryForObject("SELECT TOP 1 id FROM user_role WHERE user_id = ? AND role = "
					+ "'DEPARTMENT_MANAGER'", Long.class, julien);
			ApiClient admin = signedIn("admin@demo.local");

			assertThat(admin.post("/api/v1/admin/accounts/" + julien + "/roles",
					"{\"role\":\"BUYER\",\"companyId\":" + fr + ",\"departmentId\":null}").expectStatus(403).code())
				.isEqualTo("peer-administrator");
			assertThat(admin.post("/api/v1/admin/accounts/" + julien + "/roles/" + managerGrant + "/remove", null)
				.expectStatus(403)
				.code()).isEqualTo("peer-administrator");
			admin.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(403);

			assertThat(roleCount(julien)).isEqualTo(roles);
			// No role change, so the peer was not signed out.
			assertThat(credentialsVersion(julien)).isEqualTo(version);
			peer.get("/api/v1/me").expectStatus(200);

			// Administrators are managed by global administrators.
			global.post("/api/v1/admin/accounts/" + julien + "/roles",
					"{\"role\":\"REQUESTER\",\"companyId\":" + fr + ",\"departmentId\":null}").expectStatus(200);
			long requesterGrant = jdbc.queryForObject("SELECT id FROM user_role WHERE user_id = ? AND role = 'REQUESTER' "
					+ "AND company_id = ?", Long.class, julien, fr);
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + requesterGrant + "/remove", null)
				.expectStatus(200);
		}
		finally {
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + adminGrant + "/remove", null).expectStatus(200);
		}
	}

	private int roleCount(long user) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id = ?", Integer.class, user);
	}

	private int credentialsVersion(long user) {
		return jdbc.queryForObject("SELECT credentials_version FROM user_account WHERE id = ?", Integer.class, user);
	}

	// ---- S-6: audit events

	@Test
	void everyAbsenceRecordedOrCancelledIsAudited() {
		ApiClient admin = signedIn("admin@demo.local");
		long karim = userId("manager.tn@demo.local");
		long absence = admin.post("/api/v1/admin/absences",
				"{\"userId\":" + karim + ",\"delegateUserId\":" + userId("manager2.tn@demo.local") + period())
			.expectStatus(201)
			.id();
		assertThat(auditCount("USER_ABSENCE", Long.toString(absence), "ABSENCE_CREATED")).isEqualTo(1);
		Map<String, Object> created = jdbc.queryForMap("SELECT actor_user_id, company_id, payload_json FROM audit_event "
				+ "WHERE entity_type = 'USER_ABSENCE' AND entity_id = ? AND action = 'ABSENCE_CREATED'",
				Long.toString(absence));
		assertThat(((Number) created.get("actor_user_id")).longValue()).isEqualTo(userId("admin@demo.local"));
		assertThat(((Number) created.get("company_id")).longValue()).isEqualTo(companyId("DEMO-TN-PLT"));
		var payload = JSON.readTree((String) created.get("payload_json"));
		assertThat(payload.get("userId").asLong()).isEqualTo(karim);
		assertThat(payload.get("delegateUserId").asLong()).isEqualTo(userId("manager2.tn@demo.local"));
		assertThat(payload.get("byAdministrator").asBoolean()).isTrue();

		admin.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
		admin.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
		assertThat(auditCount("USER_ABSENCE", Long.toString(absence), "ABSENCE_CANCELLED")).isEqualTo(1);

		// Self-service absences are audited too.
		ApiClient karimClient = signedIn("manager.tn@demo.local");
		long own = karimClient.post("/api/v1/me/absences",
				"{\"delegateUserId\":" + userId("manager2.tn@demo.local") + period())
			.expectStatus(201)
			.id();
		karimClient.delete("/api/v1/me/absences/" + own).expectStatus(204);
		assertThat(auditCount("USER_ABSENCE", Long.toString(own), "ABSENCE_CREATED")).isEqualTo(1);
		assertThat(auditCount("USER_ABSENCE", Long.toString(own), "ABSENCE_CANCELLED")).isEqualTo(1);
	}

	@Test
	void everyApprovalRuleChangeIsAudited() {
		ApiClient admin = signedIn("admin@demo.local");
		long fr = companyId("DEMO-FR-PLT");
		long julien = userId("manager.fr@demo.local");
		Map<String, Object> stage = Map.of("name", "Named", "approverType", "NAMED_USER", "approverUserId", julien,
				"policy", "ANY", "slaHours", 8);
		long rule = admin.post("/api/v1/admin/approval-rules",
				JSON.writeValueAsString(Map.of("companyId", fr, "name", "Audit \"quoted\" (test)", "purchaseType",
						"INTERNATIONAL", "minAmount", 900000, "maxAmount", 900001, "stages", List.of(stage))))
			.expectStatus(201)
			.id();
		assertThat(auditCount("APPROVAL_RULE", Long.toString(rule), "APPROVAL_RULE_CREATED")).isEqualTo(1);

		admin.put("/api/v1/admin/approval-rules/" + rule,
				JSON.writeValueAsString(Map.of("name", "Audit renamed (test)", "purchaseType", "INTERNATIONAL", "minAmount",
						900000, "maxAmount", 900002, "stages", List.of(stage))))
			.expectStatus(200);
		var edit = JSON.readTree(jdbc.queryForObject("SELECT payload_json FROM audit_event WHERE entity_type = "
				+ "'APPROVAL_RULE' AND entity_id = ? AND action = 'APPROVAL_RULE_UPDATED'", String.class,
				Long.toString(rule)));
		assertThat(edit.get("before").get("name").asString()).isEqualTo("Audit \"quoted\" (test)");
		assertThat(edit.get("after").get("name").asString()).isEqualTo("Audit renamed (test)");
		assertThat(edit.get("before").get("maxAmount").asString()).startsWith("900001");
		assertThat(edit.get("after").get("maxAmount").asString()).startsWith("900002");
		assertThat(edit.get("after").get("stages").get(0).get("approverUserId").asLong()).isEqualTo(julien);

		admin.post("/api/v1/admin/approval-rules/" + rule + "/activate", null).expectStatus(200);
		assertThat(auditCount("APPROVAL_RULE", Long.toString(rule), "APPROVAL_RULE_ACTIVATED")).isEqualTo(1);

		// Editing an active rule creates a new version, audited with the version it replaces.
		long version = admin.put("/api/v1/admin/approval-rules/" + rule,
				JSON.writeValueAsString(Map.of("name", "Audit v2 (test)", "purchaseType", "INTERNATIONAL", "minAmount", 900000,
						"maxAmount", 900003, "stages", List.of(stage))))
			.expectStatus(200)
			.id();
		assertThat(version).isNotEqualTo(rule);
		assertThat(auditCount("APPROVAL_RULE", Long.toString(version), "APPROVAL_RULE_VERSION_CREATED")).isEqualTo(1);

		admin.post("/api/v1/admin/approval-rules/" + rule + "/retire", null).expectStatus(200);
		assertThat(auditCount("APPROVAL_RULE", Long.toString(rule), "APPROVAL_RULE_RETIRED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_type = 'APPROVAL_RULE' "
				+ "AND entity_id IN (?, ?) AND company_id = ?", Integer.class, Long.toString(rule),
				Long.toString(version), fr)).isEqualTo(5);
	}

	@Test
	void everyExchangeRateIsAudited() {
		ApiClient admin = signedIn("admin@demo.local");
		// An old date, so the rate never becomes the current one for other tests.
		long rate = admin.post("/api/v1/admin/exchange-rates", JSON.writeValueAsString(Map.of("baseCurrency", "EUR",
				"quoteCurrency", "TND", "rate", "3.41234567", "rateDate", LocalDate.of(2001, 1, 1).toString(), "source",
				"Audit \"test\"")))
			.expectStatus(201)
			.id();
		var payload = JSON.readTree(jdbc.queryForObject("SELECT payload_json FROM audit_event WHERE entity_type = "
				+ "'EXCHANGE_RATE' AND entity_id = ? AND action = 'EXCHANGE_RATE_ADDED'", String.class,
				Long.toString(rate)));
		assertThat(payload.get("rate").asString()).isEqualTo("3.41234567");
		assertThat(payload.get("source").asString()).isEqualTo("Audit \"test\"");
	}

	// ---- S-7: company scope

	@Test
	void aCompanyAdministratorListsOnlyTheirOwnCompaniesUsersAndAbsences() {
		ApiClient global = signedIn("global.admin@demo.local");
		long julien = userId("manager.fr@demo.local");
		long karim = userId("manager.tn@demo.local");
		long fr = companyId("DEMO-FR-PLT");
		long tn = companyId("DEMO-TN-PLT");
		long grant = grantAdmin(global, julien, "DEMO-FR-PLT");
		long absence = signedIn("admin@demo.local").post("/api/v1/admin/absences",
				"{\"userId\":" + karim + ",\"delegateUserId\":" + userId("manager2.tn@demo.local") + period())
			.expectStatus(201)
			.id();
		try {
			ApiClient frAdmin = signedIn("manager.fr@demo.local");

			assertThat((List<String>) frAdmin.get("/api/v1/admin/companies").expectStatus(200).json("$[*].code"))
				.containsExactly("DEMO-FR-PLT");

			List<String> emails = frAdmin.get("/api/v1/admin/users").expectStatus(200).json("$[*].email");
			// Own company, plus people who hold a role there (the CEO approves for the French plant).
			assertThat(emails).contains("requester.fr@demo.local", "manager.fr@demo.local", "ceo@demo.local")
				.doesNotContain("requester.tn@demo.local", "manager.tn@demo.local", "budget.tn@demo.local",
						"buyer@demo.local", "receiver.tn@demo.local");
			assertThat((List<String>) frAdmin.get("/api/v1/admin/users?q=Sami").expectStatus(200).json("$[*].email"))
				.isEmpty();
			frAdmin.get("/api/v1/admin/users?companyId=" + tn).expectStatus(404);
			assertThat((List<String>) frAdmin.get("/api/v1/admin/users?companyId=" + fr).expectStatus(200)
				.json("$[*].email")).contains("requester.fr@demo.local");

			assertThat((List<Integer>) frAdmin.get("/api/v1/admin/absences").expectStatus(200).json("$[*].id"))
				.doesNotContain((int) absence);
			frAdmin.get("/api/v1/admin/absences?userId=" + karim).expectStatus(404);
			frAdmin.delete("/api/v1/admin/absences/" + absence).expectStatus(404);
			frAdmin.get("/api/v1/admin/approval-rules?companyId=" + tn).expectStatus(404);

			// The whole group remains visible to a global administrator.
			assertThat((List<String>) global.get("/api/v1/admin/companies").expectStatus(200).json("$[*].code"))
				.contains("DEMO-TN-PLT", "DEMO-FR-PLT", "DEMO-CPA");
			assertThat((List<String>) global.get("/api/v1/admin/users").expectStatus(200).json("$[*].email"))
				.contains("requester.tn@demo.local", "requester.fr@demo.local");
			assertThat((List<Integer>) global.get("/api/v1/admin/absences").expectStatus(200).json("$[*].id"))
				.contains((int) absence);
		}
		finally {
			signedIn("admin@demo.local").delete("/api/v1/admin/absences/" + absence).expectStatus(204);
			global.post("/api/v1/admin/accounts/" + julien + "/roles/" + grant + "/remove", null).expectStatus(200);
		}
	}

	// ---- helpers

	private String period() {
		Instant now = clock.instant();
		return ",\"startsAt\":\"" + now.plus(Duration.ofDays(400)) + "\",\"endsAt\":\"" + now.plus(Duration.ofDays(401))
				+ "\"}";
	}

	private long grantAdmin(ApiClient global, long user, String companyCode) {
		global.post("/api/v1/admin/accounts/" + user + "/roles",
				"{\"role\":\"ADMIN\",\"companyId\":" + companyId(companyCode) + ",\"departmentId\":null}")
			.expectStatus(200);
		return jdbc.queryForObject("SELECT id FROM user_role WHERE user_id = ? AND role = 'ADMIN' AND company_id = ?",
				Long.class, user, companyId(companyCode));
	}

	private long createUser(ApiClient global, String prefix) {
		String email = prefix + "." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long id = global.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Sprint One User","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		activateByTheOtherAdministrator(id);
		return id;
	}

	private String passwordHash(long user) {
		return jdbc.queryForObject("SELECT password_hash FROM user_credential WHERE user_id = ?", String.class, user);
	}

	private int absenceCount(long user) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM user_absence WHERE user_id = ?", Integer.class, user);
	}

	private int auditCount(String entityType, String entityId, String action) {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM audit_event WHERE entity_type = ? AND entity_id = ? AND action = ?", Integer.class,
				entityType, entityId, action);
	}

}
