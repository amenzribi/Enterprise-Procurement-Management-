package com.epi.procurement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 2, ADR-016 step 5: a global administrator corrects an account's e-mail address. Nothing changes before the
 * owner of the new mailbox proves control; sensitive accounts need a second global administrator; activation and
 * links wait; every step and refusal is audited.
 */
class EmailChangeIntegrationTests extends SqlServerTestSupport {

	@Autowired
	EmailChangeTokens tokens;

	@Autowired
	com.epi.procurement.notification.OutboxSender outbox;

	private static String unique(String prefix) {
		return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
	}

	/** An account created by the company administrator and activated by the global administrator (F-1). */
	private long account(boolean activate) {
		long id = signedIn("admin@demo.local").post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Mail Change","companyId":%d,"preferredLanguage":"en","roles":[]}
				""".formatted(unique("s2.mail"), companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		if (activate) {
			signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + id + "/active", "{\"active\":true}")
				.expectStatus(200);
		}
		return id;
	}

	/** Gives an account the demo password, so a test can sign in as it. */
	private void demoPassword(long userId) {
		jdbc.update("""
				UPDATE user_credential SET password_hash = (SELECT password_hash FROM user_credential WHERE user_id = ?),
				must_change_password = 0 WHERE user_id = ?""", userId("global.admin@demo.local"), userId);
	}

	private String email(long userId) {
		return jdbc.queryForObject("SELECT email FROM user_account WHERE id = ?", String.class, userId);
	}

	private int audit(long userId, String action) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_type = 'USER_ACCOUNT' AND entity_id = ? "
				+ "AND action = ?", Integer.class, Long.toString(userId), action);
	}

	private int mails(String kind, String address) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE kind = ? AND recipient_email = ?",
				Integer.class, kind, address);
	}

	private long changeId(long userId) {
		return jdbc.queryForObject("SELECT MAX(id) FROM email_change_request WHERE user_id = ?", Long.class, userId);
	}

	private static String token(String linkPath) {
		assertThat(linkPath).startsWith("/e#");
		return linkPath.substring(3);
	}

	@Test
	void anOrdinaryAccountChangesAddressOnlyWhenTheNewMailboxConfirms() throws Exception {
		long user = account(true);
		demoPassword(user);
		String before = email(user);
		ApiClient owner = client();
		owner.login(before, DEMO_PASSWORD).expectStatus(200);
		ApiClient global = signedIn("global.admin@demo.local");
		String next = unique("s2.mail.new");

		assertThat((String) global.post("/api/v1/admin/accounts/" + user + "/email-change",
				"{\"newEmail\":\"" + next.toUpperCase() + "\",\"reason\":\"Typo at creation\"}").expectStatus(202).json("$.status"))
			.isEqualTo("PENDING_VERIFICATION");
		// Nothing changed yet; the link goes to the new mailbox; no reset link while the change is open.
		assertThat(email(user)).isEqualTo(before);
		assertThat(mails("EMAIL_CHANGE_CONFIRM", next)).isEqualTo(1);
		assertThat(global.post("/api/v1/admin/accounts/" + user + "/password-reset", "{}").expectStatus(409).code())
			.isEqualTo("email-change-pending");
		assertThat(audit(user, "EMAIL_CHANGE_REQUESTED")).isEqualTo(1);

		// The link in the e-mail actually written for the new mailbox (file mail mode), not one made by the test.
		outbox.sendDue();
		String raw = com.epi.procurement.support.SentMail.linkToken(next, "e");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE body_html LIKE ? OR body_text LIKE ?",
				Integer.class, "%" + raw + "%", "%" + raw + "%")).as("no usable token stored in the outbox").isZero();
		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.post("/api/v1/auth/email-change/confirm", "{\"token\":\"" + raw + "\"}").expectStatus(204);

		assertThat(email(user)).isEqualTo(next);
		assertThat(audit(user, "EMAIL_CHANGED")).isEqualTo(1);
		assertThat(mails("EMAIL_CHANGED", before)).isEqualTo(1);
		// Sessions ended; the link is single use; the owner signs in with the new address.
		owner.get("/api/v1/me").expectStatus(401);
		anonymous.post("/api/v1/auth/email-change/confirm", "{\"token\":\"" + raw + "\"}").expectStatus(404);
		client().login(next, DEMO_PASSWORD).expectStatus(200);
		client().login(before, DEMO_PASSWORD).expectStatus(401);
	}

	@Test
	void anInactiveAccountIsNotActivatedWhileItsAddressIsBeingCorrected() {
		long user = account(false);
		ApiClient global = signedIn("global.admin@demo.local");
		global.post("/api/v1/admin/accounts/" + user + "/email-change",
				"{\"newEmail\":\"" + unique("s2.mail.fix") + "\",\"reason\":\"Wrong address before activation\"}")
			.expectStatus(202);
		assertThat(global.post("/api/v1/admin/accounts/" + user + "/active", "{\"active\":true}").expectStatus(409).code())
			.isEqualTo("email-change-pending");
		assertThat(jdbc.queryForObject("SELECT CAST(is_active AS INT) FROM user_account WHERE id = ?", Integer.class, user))
			.isZero();
		global.post("/api/v1/admin/accounts/" + user + "/email-change/cancel", "{}").expectStatus(204);
		assertThat(audit(user, "EMAIL_CHANGE_CANCELLED")).isEqualTo(1);
		global.post("/api/v1/admin/accounts/" + user + "/active", "{\"active\":true}").expectStatus(200);
	}

	@Test
	void aSensitiveAccountNeedsAnIndependentSecondGlobalAdministrator() {
		long user = account(true);
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'BUYER', ?)", user, companyId("DEMO-TN-PLT"));
		// A second global administrator (as the bootstrap would create), able to sign in.
		long second = account(true);
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'GLOBAL_ADMIN', NULL)", second);
		demoPassword(second);
		try {
			ApiClient global = signedIn("global.admin@demo.local");
			String next = unique("s2.mail.buyer");
			assertThat((String) global.post("/api/v1/admin/accounts/" + user + "/email-change",
					"{\"newEmail\":\"" + next + "\",\"reason\":\"Mailbox renamed\"}").expectStatus(202).json("$.status"))
				.isEqualTo("PENDING_APPROVAL");
			assertThat(mails("EMAIL_CHANGE_CONFIRM", next)).isZero();
			// The requester cannot approve their own request; the refusal is recorded.
			assertThat(global.post("/api/v1/admin/accounts/" + user + "/email-change/approve", "{}").expectStatus(403).code())
				.isEqualTo("email-change-four-eyes");
			assertThat(audit(user, "EMAIL_CHANGE_REFUSED")).isEqualTo(1);

			ApiClient other = client();
			other.login(email(second), DEMO_PASSWORD).expectStatus(200);
			assertThat((String) other.post("/api/v1/admin/accounts/" + user + "/email-change/approve", "{}")
				.expectStatus(200)
				.json("$.status")).isEqualTo("PENDING_VERIFICATION");
			assertThat(mails("EMAIL_CHANGE_CONFIRM", next)).isEqualTo(1);
			assertThat(audit(user, "EMAIL_CHANGE_APPROVED")).isEqualTo(1);
		}
		finally {
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'GLOBAL_ADMIN'", second);
		}
	}

	@Test
	void refusedAttemptsAreAnsweredAndRecorded() {
		long user = account(true);
		ApiClient companyAdmin = signedIn("admin@demo.local");
		assertThat(companyAdmin.post("/api/v1/admin/accounts/" + user + "/email-change",
				"{\"newEmail\":\"" + unique("x") + "\",\"reason\":\"Company admin try\"}").expectStatus(403).code())
			.isEqualTo("forbidden");
		assertThat(audit(user, "EMAIL_CHANGE_REFUSED")).isEqualTo(1);

		ApiClient global = signedIn("global.admin@demo.local");
		long self = userId("global.admin@demo.local");
		assertThat(global.post("/api/v1/admin/accounts/" + self + "/email-change",
				"{\"newEmail\":\"" + unique("self") + "\",\"reason\":\"Own address\"}").expectStatus(403).code())
			.isEqualTo("self-email-change");
		long contact = userId("contact.skf@demo.local");
		assertThat(global.post("/api/v1/admin/accounts/" + contact + "/email-change",
				"{\"newEmail\":\"" + unique("contact") + "\",\"reason\":\"Supplier contact\"}").expectStatus(403).code())
			.isEqualTo("supplier-contact-account");
		assertThat(global.post("/api/v1/admin/accounts/" + user + "/email-change",
				"{\"newEmail\":\"requester.tn@demo.local\",\"reason\":\"Taken address\"}").expectStatus(409).code())
			.isEqualTo("email-in-use");
		global.post("/api/v1/admin/accounts/" + user + "/email-change", "{\"newEmail\":\"not an address\",\"reason\":\"Bad\"}")
			.expectStatus(400);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_change_request WHERE user_id = ?", Integer.class, user))
			.isZero();
	}

	@Test
	void anExpiredLinkGetsTheNeutralAnswerAndClosesTheChange() {
		long user = account(true);
		String before = email(user);
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + user + "/email-change",
				"{\"newEmail\":\"" + unique("s2.mail.late") + "\",\"reason\":\"Late confirmation\"}").expectStatus(202);
		String raw = token(tokens.createLinkPath(changeId(user)));
		jdbc.update("UPDATE email_change_request SET token_expires_at = DATEADD(HOUR, -1, SYSUTCDATETIME()) WHERE id = ?",
				changeId(user));
		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.post("/api/v1/auth/email-change/confirm", "{\"token\":\"" + raw + "\"}").expectStatus(404);
		assertThat(email(user)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT status FROM email_change_request WHERE id = ?", String.class, changeId(user)))
			.isEqualTo("EXPIRED");
		assertThat(audit(user, "EMAIL_CHANGE_CONFIRM_REFUSED")).isEqualTo(1);
		// An unknown token: the same answer.
		anonymous.post("/api/v1/auth/email-change/confirm", "{\"token\":\"unknown\"}").expectStatus(404);
	}

}
