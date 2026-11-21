package com.epi.procurement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 10 GLOBAL_ADMIN user administration, password setup/reset and last-admin protection.
 */
class UserAdminIntegrationTests extends SqlServerTestSupport {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordResetTokens passwordResetTokens;

	@Test
	void globalAdminCanCreateUserAndOrdinaryUserIsDenied() {
		ApiClient admin = signedIn("global.admin@demo.local");
		assertThat((List<String>) admin.get("/api/v1/me").expectStatus(200).json("$.permissions"))
			.contains("USER_ADMIN", "USER_ADMIN_GLOBAL");

		long companyId = ((Number) admin.get("/api/v1/admin/organization/companies").expectStatus(200).json("$[0].id"))
			.longValue();
		String email = "s10.user." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		ApiClient.Response created = admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Sprint Ten User","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId)).expectStatus(201);
		long userId = ((Number) created.json("$.id")).longValue();
		assertThat((String) created.json("$.email")).isEqualTo(email);

		ApiClient sami = signedIn("requester.tn@demo.local");
		sami.get("/api/v1/admin/accounts").expectStatus(403);

		admin.post("/api/v1/admin/accounts/" + userId + "/roles",
				"""
						{"role":"REQUESTER","companyId":%d,"departmentId":null}
						""".formatted(companyId)).expectStatus(200);
		assertThat((List<?>) admin.get("/api/v1/admin/accounts/" + userId).expectStatus(200).json("$.roles")).isNotEmpty();
	}

	@Test
	void companyAdminCannotGrantGlobalAdmin() {
		ApiClient orgAdmin = signedIn("admin@demo.local");
		assertThat((List<String>) orgAdmin.get("/api/v1/me").expectStatus(200).json("$.permissions"))
			.contains("USER_ADMIN");
		long companyId = ((Number) orgAdmin.get("/api/v1/admin/organization/companies")
			.expectStatus(200)
			.json("$[0].id")).longValue();
		String email = "s10.org." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long userId = ((Number) orgAdmin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Org Scoped","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId)).expectStatus(201).json("$.id")).longValue();
		orgAdmin.post("/api/v1/admin/accounts/" + userId + "/roles",
				"""
						{"role":"GLOBAL_ADMIN","companyId":null,"departmentId":null}
						""").expectStatus(403);
	}

	@Test
	void passwordResetRedeemIsSingleUse() {
		ApiClient admin = signedIn("global.admin@demo.local");
		long companyId = ((Number) admin.get("/api/v1/admin/organization/companies").expectStatus(200).json("$[0].id"))
			.longValue();
		String email = "s10.reset." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long userId = ((Number) admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Reset User","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId)).expectStatus(201).json("$.id")).longValue();
		// F-1: a new account is inactive until another administrator activates it.
		signedIn("admin@demo.local").post("/api/v1/admin/accounts/" + userId + "/active", "{\"active\":true}")
			.expectStatus(200);

		admin.post("/api/v1/admin/accounts/" + userId + "/password-reset", "{}").expectStatus(202);
		String path = passwordResetTokens.createLinkPath(userId, "RESET", null);
		assertThat(path).startsWith("/p#");
		String raw = path.substring("/p#".length());

		String password = "ResetPass12ab";
		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.post("/api/v1/auth/password-reset/redeem",
				"{\"token\":\"" + raw + "\",\"newPassword\":\"" + password + "\"}").expectStatus(204);
		anonymous.post("/api/v1/auth/password-reset/redeem",
				"{\"token\":\"" + raw + "\",\"newPassword\":\"" + password + "x\"}").expectStatus(404);

		ApiClient signed = client();
		signed.login(email, password).expectStatus(200);
		assertThat((Boolean) signed.get("/api/v1/me").expectStatus(200).json("$.mustChangePassword")).isFalse();
	}

	@Test
	void lastActiveGlobalAdminCannotBeDeactivated() {
		ApiClient admin = signedIn("global.admin@demo.local");
		long adminId = ((Number) admin.get("/api/v1/me").expectStatus(200).json("$.id")).longValue();
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(DISTINCT u.id) FROM user_account u
				JOIN user_role r ON r.user_id = u.id AND r.role = 'GLOBAL_ADMIN' WHERE u.is_active = 1""", Integer.class);
		assertThat(count).isGreaterThanOrEqualTo(1);
		admin.post("/api/v1/admin/accounts/" + adminId + "/active", "{\"active\":false}").expectStatus(409);
	}

	@Test
	void companyAdminListIsScopedBeforePagination() {
		ApiClient admin = signedIn("admin@demo.local");
		Integer inScope = jdbc.queryForObject("""
				SELECT COUNT(*) FROM user_account u WHERE u.company_id IN (
				    SELECT company_id FROM user_role WHERE role = 'ADMIN' AND company_id IS NOT NULL
				    AND user_id = (SELECT id FROM user_account WHERE email = 'admin@demo.local'))""", Integer.class);
		assertThat(inScope).isPositive();
		// Every page up to the in-scope total must be full: scope applied after OFFSET/FETCH leaves holes.
		for (int page = 0; page < inScope; page++) {
			List<?> items = admin.get("/api/v1/admin/accounts?page=" + page + "&size=1").expectStatus(200).json("$");
			assertThat(items).as("page %d", page).hasSize(1);
		}
		List<?> beyond = admin.get("/api/v1/admin/accounts?page=" + inScope + "&size=1").expectStatus(200).json("$");
		assertThat(beyond).isEmpty();
	}

	@Test
	void companyAdminCannotRemoveARoleFromAGlobalAdmin() {
		long adminCompany = jdbc.queryForObject("""
				SELECT TOP 1 company_id FROM user_role WHERE role = 'ADMIN' AND company_id IS NOT NULL
				AND user_id = (SELECT id FROM user_account WHERE email = 'admin@demo.local')""", Long.class);
		ApiClient global = signedIn("global.admin@demo.local");
		long globalId = ((Number) global.get("/api/v1/me").expectStatus(200).json("$.id")).longValue();
		// Fixture: a global administrator holding a company role. Set directly, because nobody may give themselves
		// a sensitive role through the API (review RR-3) and the demo has a single global administrator.
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'BUYER', ?)", globalId, adminCompany);
		long grantId = jdbc.queryForObject("SELECT id FROM user_role WHERE user_id = ? AND role = 'BUYER' AND company_id = ?",
				Long.class, globalId, adminCompany);
		signedIn("admin@demo.local").post("/api/v1/admin/accounts/" + globalId + "/roles/" + grantId + "/remove", "{}").expectStatus(403);
		// Changing a role ends that account's sessions, so the clean-up signs in again.
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + globalId + "/roles/" + grantId + "/remove", "{}")
			.expectStatus(200);
	}

	@Test
	void secondGlobalAdminCanBeDeactivatedButNotBelowOne() {
		ApiClient admin = signedIn("global.admin@demo.local");
		long companyId = ((Number) admin.get("/api/v1/admin/organization/companies").expectStatus(200).json("$[0].id"))
			.longValue();
		String email = "s10.ga2." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long secondId = ((Number) admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Second GA","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId)).expectStatus(201).json("$.id")).longValue();
		// F-2: the creator never grants GLOBAL_ADMIN; a second global administrator comes from the bootstrap
		// (GLOBAL_ADMIN_BOOTSTRAP_SECOND_EMAIL), simulated here by a direct insert, and is active.
		assertThat(admin.post("/api/v1/admin/accounts/" + secondId + "/roles",
				"""
						{"role":"GLOBAL_ADMIN","companyId":null,"departmentId":null}
						""").expectStatus(403).code()).isEqualTo("four-eyes");
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'GLOBAL_ADMIN', NULL)", secondId);
		jdbc.update("UPDATE user_account SET is_active = 1 WHERE id = ?", secondId);
		admin.post("/api/v1/admin/accounts/" + secondId + "/active", "{\"active\":false}").expectStatus(200);
		long firstId = ((Number) admin.get("/api/v1/me").expectStatus(200).json("$.id")).longValue();
		admin.post("/api/v1/admin/accounts/" + firstId + "/active", "{\"active\":false}").expectStatus(409);
	}

}
