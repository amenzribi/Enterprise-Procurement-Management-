package com.epi.procurement.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SentMail;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * The links people receive work (Sprint 2 regression): the set-up, administrator reset and self-service reset
 * e-mails are sent through the outbox and the token is taken from the e-mail file, then redeemed. Since Sprint 10
 * these e-mails had carried the raw placeholder {@code {{link:password-reset:...}}} because the sender's pattern
 * accepted neither a hyphen in the kind nor a code as argument; every test made its own token and passed.
 */
class MailLinkIntegrationTests extends SqlServerTestSupport {

	@Autowired
	OutboxSender outbox;

	private static final String NEW_PASSWORD = "Mailed-Link-Pass-12";

	private long newAccount(String email) {
		long id = signedIn("admin@demo.local").post("/api/v1/admin/accounts", """
				{"email":"%s","displayName":"Mail Link","companyId":%d,"preferredLanguage":"en","roles":[]}
				""".formatted(email, companyId("DEMO-TN-PLT"))).expectStatus(201).id();
		// F-1: activated by another administrator; activation sends the set-up link.
		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + id + "/active", "{\"active\":true}")
			.expectStatus(200);
		return id;
	}

	private void redeem(String token, String password) {
		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.post("/api/v1/auth/password-reset/redeem",
				"{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}").expectStatus(204);
	}

	private static String unique() {
		return "s2.link." + UUID.randomUUID().toString().substring(0, 8) + "@demo.local";
	}

	@Test
	void theSetUpEmailCarriesAWorkingLink() throws Exception {
		String email = unique();
		newAccount(email);
		outbox.sendDue();
		redeem(SentMail.linkToken(email, "p"), NEW_PASSWORD);
		client().login(email, NEW_PASSWORD).expectStatus(200);
	}

	@Test
	void theResetEmailsAskedByAnAdministratorOrByTheOwnerCarryWorkingLinks() throws Exception {
		String email = unique();
		long id = newAccount(email);
		outbox.sendDue();
		redeem(SentMail.linkToken(email, "p"), NEW_PASSWORD);

		signedIn("global.admin@demo.local").post("/api/v1/admin/accounts/" + id + "/password-reset", "{}")
			.expectStatus(202);
		outbox.sendDue();
		redeem(SentMail.linkToken(email, "p"), NEW_PASSWORD + "b");
		client().login(email, NEW_PASSWORD + "b").expectStatus(200);

		ApiClient anonymous = client();
		anonymous.csrf();
		anonymous.post("/api/v1/auth/password-reset/request", "{\"email\":\"" + email + "\"}").expectStatus(202);
		outbox.sendDue();
		redeem(SentMail.linkToken(email, "p"), NEW_PASSWORD + "c");
		client().login(email, NEW_PASSWORD + "c").expectStatus(200);
	}

	@Test
	void aPlaceholderTheSenderCannotMatchIsRefusedWhenItIsMade() {
		assertThat(NoticeLinkResolver.placeholder("password-reset", 7, "SETUP", 0))
			.isEqualTo("{{link:password-reset:7:SETUP:0}}");
		assertThatThrownBy(() -> NoticeLinkResolver.placeholder("Password_Reset", 7))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> NoticeLinkResolver.placeholder("approval", "a:b"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
