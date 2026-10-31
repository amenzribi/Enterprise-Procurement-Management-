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
	void secondGlobalAdminCanBeDeactivatedButNotBelowOne() {
		ApiClient admin = signedIn("global.admin@demo.local");
		long companyId = ((Number) admin.get("/api/v1/admin/organization/companies").expectStatus(200).json("$[0].id"))
			.longValue();
		String email = "s10.ga2." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
		long secondId = ((Number) admin.post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Second GA","companyId":%d,"preferredLanguage":"en","active":true,"roles":[]}
				""".formatted(email, companyId)).expectStatus(201).json("$.id")).longValue();
		admin.post("/api/v1/admin/accounts/" + secondId + "/roles",
				"""
						{"role":"GLOBAL_ADMIN","companyId":null,"departmentId":null}
						""").expectStatus(200);
		admin.post("/api/v1/admin/accounts/" + secondId + "/active", "{\"active\":false}").expectStatus(200);
		long firstId = ((Number) admin.get("/api/v1/me").expectStatus(200).json("$.id")).longValue();
		admin.post("/api/v1/admin/accounts/" + firstId + "/active", "{\"active\":false}").expectStatus(409);
	}

}
