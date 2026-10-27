package com.epi.procurement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sign-in, sessions, CSRF, throttling, diagnostics exposure and language preference over real HTTP against the
 * running server and SQL Server (S1-AC-01, 02, 12, 13).
 */
class AuthenticationIntegrationTests extends SqlServerTestSupport {

	@Test
	void signInReturnsTheUserAndSetsAHardenedSessionCookie() {
		ApiClient client = client();
		client.csrf().expectStatus(204);
		assertThat(client.cookie("XSRF-TOKEN")).isPresent();

		ApiClient.Response me = client.login("requester.fr@demo.local", DEMO_PASSWORD).expectStatus(200);

		assertThat((String) me.json("$.email")).isEqualTo("requester.fr@demo.local");
		assertThat((String) me.json("$.company.code")).isEqualTo("DEMO-FR-PLT");
		assertThat((List<String>) me.json("$.roles")).containsExactly("REQUESTER");
		assertThat((List<String>) me.json("$.permissions")).contains("REQUEST_CREATE");
		assertThat((String) me.json("$.sessionExpiresAt")).isNotBlank();
		assertThat((Integer) me.json("$.idleTimeoutSeconds")).isEqualTo(1800);
		assertThat((Integer) me.json("$.absoluteTimeoutSeconds")).isEqualTo(28800);
		assertThat((Boolean) me.json("$.demo")).isTrue();
		String sessionCookie = me.setCookies().stream().filter(c -> c.startsWith("EPI_SESSION=")).findFirst().orElseThrow();
		assertThat(sessionCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax");

		client.get("/api/v1/me").expectStatus(200);
	}

	@Test
	void wrongPasswordAndUnknownEmailAreIndistinguishable() {
		ApiClient.Response wrongPassword = client().login("requester.tn@demo.local", "not-the-password").expectStatus(401);
		ApiClient.Response unknown = client().login("nobody@demo.local", "not-the-password").expectStatus(401);

		assertThat(wrongPassword.code()).isEqualTo("invalid-credentials");
		assertThat(unknown.body()).isEqualTo(wrongPassword.body());
	}

	@Test
	void stateChangingRequestsRequireTheCsrfHeader() {
		ApiClient client = client();
		client.csrf();
		ApiClient.Response refused = client.withoutCsrfHeader().login("requester.fr@demo.local", DEMO_PASSWORD);

		refused.expectStatus(403);
		assertThat(refused.code()).isEqualTo("csrf-invalid");

		ApiClient signedIn = signedIn("requester.fr@demo.local");
		ApiClient.Response noHeader = signedIn.withoutCsrfHeader().post("/api/v1/requests", "{\"title\":\"x\"}");
		noHeader.expectStatus(403);
		assertThat(noHeader.code()).isEqualTo("csrf-invalid");
	}

	@Test
	void signInRotatesTheSessionIdAndTheCsrfToken() {
		ApiClient client = client();
		client.csrf();
		String tokenBefore = client.cookie("XSRF-TOKEN").orElseThrow();
		client.login("requester.fr@demo.local", DEMO_PASSWORD).expectStatus(200);
		String firstSession = client.cookie("EPI_SESSION").orElseThrow();
		assertThat(client.cookie("XSRF-TOKEN").orElseThrow()).isNotEqualTo(tokenBefore);

		// Signing in again on an existing session changes its id (session fixation protection).
		client.login("requester.fr@demo.local", DEMO_PASSWORD).expectStatus(200);
		String secondSession = client.cookie("EPI_SESSION").orElseThrow();
		assertThat(secondSession).isNotEqualTo(firstSession);

		ApiClient replay = client();
		replay.setCookie("EPI_SESSION", firstSession);
		replay.get("/api/v1/me").expectStatus(401);
		client.get("/api/v1/me").expectStatus(200);
	}

	@Test
	void signOutInvalidatesTheSessionOnTheServer() {
		ApiClient client = signedIn("requester.fr@demo.local");
		String session = client.cookie("EPI_SESSION").orElseThrow();

		client.logout().expectStatus(204);

		client.get("/api/v1/me").expectStatus(401);
		ApiClient replay = client();
		replay.setCookie("EPI_SESSION", session);
		assertThat(replay.get("/api/v1/me").expectStatus(401).code()).isIn("session-expired", "unauthenticated");
		// Signing out without a session is harmless.
		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.logout().expectStatus(204);
	}

	@Test
	void sessionsExpireAfterThirtyMinutesOfInactivityWithASlidingWindow() {
		ApiClient client = signedIn("manager.tn@demo.local");

		clock.advance(Duration.ofMinutes(20));
		client.get("/api/v1/me").expectStatus(200);
		clock.advance(Duration.ofMinutes(20));
		client.get("/api/v1/me").expectStatus(200);

		clock.advance(Duration.ofMinutes(31));
		ApiClient.Response expired = client.get("/api/v1/me").expectStatus(401);
		assertThat(expired.code()).isEqualTo("session-expired");
	}

	@Test
	void sessionsExpireEightHoursAfterSignInWhateverTheActivity() {
		ApiClient client = signedIn("manager.tn@demo.local");

		for (int step = 1; step <= 16; step++) {
			clock.advance(Duration.ofMinutes(29));
			client.get("/api/v1/me").expectStatus(200);
		}
		clock.advance(Duration.ofMinutes(29)); // 8 h 13 min after sign-in
		assertThat(client.get("/api/v1/me").expectStatus(401).code()).isEqualTo("session-expired");
	}

	@Test
	void repeatedFailuresThrottleTheAccountFromThatAddressWithTheSameAnswer() {
		ApiClient client = client();
		ApiClient.Response first = client.login("buyer@demo.local", "wrong-1").expectStatus(401);
		for (int i = 2; i <= 5; i++) {
			client.login("buyer@demo.local", "wrong-" + i).expectStatus(401);
		}

		ApiClient.Response throttled = client.login("buyer@demo.local", DEMO_PASSWORD).expectStatus(401);
		assertThat(throttled.body()).isEqualTo(first.body());

		clock.advance(Duration.ofMinutes(16));
		client.login("buyer@demo.local", DEMO_PASSWORD).expectStatus(200);
	}

	@Test
	void diagnosticsAreRestricted() {
		ApiClient anonymous = client();
		ApiClient.Response health = anonymous.get("/actuator/health").expectStatus(200);
		assertThat(health.body()).isEqualTo("{\"status\":\"UP\"}");
		assertThat(anonymous.get("/api/v1/system/status").expectStatus(401).code()).isEqualTo("unauthenticated");
		anonymous.get("/api/v1/openapi").expectStatus(401);
		anonymous.get("/api/v1/requests").expectStatus(401);

		ApiClient requester = signedIn("requester.tn@demo.local");
		ApiClient.Response status = requester.get("/api/v1/system/status").expectStatus(200);
		assertThat((String) status.json("$.database.status")).isEqualTo("UP");
		assertThat(status.body()).doesNotContain("productVersion\":\"");
		requester.get("/api/v1/openapi").expectStatus(403);

		ApiClient admin = signedIn("admin@demo.local");
		assertThat((String) admin.get("/api/v1/system/status").expectStatus(200).json("$.database.productVersion"))
			.isNotBlank();
	}

	/**
	 * S1CR-02: roles and account state are re-read on every request.
	 */
	@Test
	void revokedRolesAndDeactivatedAccountsTakeEffectImmediately() {
		ApiClient admin = signedIn("admin@demo.local");
		long adminId = userId("admin@demo.local");
		assertThat(admin.get("/actuator/health").expectStatus(200).body()).contains("components");
		try {
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'ADMIN'", adminId);
			assertThat(admin.get("/actuator/health").expectStatus(200).body()).isEqualTo("{\"status\":\"UP\"}");
			admin.get("/api/v1/openapi").expectStatus(403);
			assertThat(admin.get("/api/v1/system/status").expectStatus(200).body()).doesNotContain("productVersion\":\"");
		}
		finally {
			jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'ADMIN')", adminId);
		}

		ApiClient ceo = signedIn("ceo@demo.local");
		long ceoId = userId("ceo@demo.local");
		try {
			jdbc.update("UPDATE user_account SET is_active = 0 WHERE id = ?", ceoId);
			ceo.get("/api/v1/me").expectStatus(401);
		}
		finally {
			jdbc.update("UPDATE user_account SET is_active = 1 WHERE id = ?", ceoId);
		}
		// The session was ended: re-activation does not revive it.
		ceo.get("/api/v1/me").expectStatus(401);
	}

	/**
	 * QA1-D-04: no default in-memory user (and no generated password) is created.
	 */
	@Test
	void noDefaultInMemoryUserExists(@org.springframework.beans.factory.annotation.Autowired
			org.springframework.security.core.userdetails.UserDetailsService userDetailsService) {
		assertThat(userDetailsService)
			.isNotInstanceOf(org.springframework.security.provisioning.InMemoryUserDetailsManager.class);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user"))
			.isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
	}

	/**
	 * QA1-D-05: one salt per demo account.
	 */
	@Test
	void demoAccountsHaveDistinctPasswordHashes() {
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(DISTINCT c.password_hash) FROM user_credential c
				JOIN user_account u ON u.id = c.user_id WHERE u.is_demo = 1""", Integer.class)).isEqualTo(8);
	}

	@Test
	void languagePreferenceIsStoredOnTheAccount() {
		ApiClient client = signedIn("purchasing.manager@demo.local");
		try {
			client.put("/api/v1/me/preferences", "{\"preferredLanguage\":\"fr\"}").expectStatus(204);
			assertThat((String) client.get("/api/v1/me").json("$.preferredLanguage")).isEqualTo("fr");

			// Another browser: the preference follows the account.
			ApiClient otherBrowser = signedIn("purchasing.manager@demo.local");
			assertThat((String) otherBrowser.get("/api/v1/me").json("$.preferredLanguage")).isEqualTo("fr");

			ApiClient.Response invalid = client.put("/api/v1/me/preferences", "{\"preferredLanguage\":\"de\"}")
				.expectStatus(422);
			assertThat(invalid.code()).isEqualTo("validation-failed");
			assertThat((String) invalid.json("$.errors[0].field")).isEqualTo("preferredLanguage");
		}
		finally {
			client.put("/api/v1/me/preferences", "{\"preferredLanguage\":\"en\"}").expectStatus(204);
		}
	}

}
