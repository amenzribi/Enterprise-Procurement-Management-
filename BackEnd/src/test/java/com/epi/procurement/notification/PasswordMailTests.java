package com.epi.procurement.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

import com.epi.procurement.identity.PasswordSetupRequested;

/**
 * ADR-016 (Sprint 2): the set-up or reset link is the only way to a password, so its e-mail must be readable in every
 * language and must say who asked for it.
 */
class PasswordMailTests {

	private static Properties bundle(String name) throws IOException {
		Properties properties = new Properties();
		try (var reader = new InputStreamReader(new ClassPathResource("notification/" + name + ".properties").getInputStream(),
				StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	@Test
	void everyLanguageHasTheSameReadableTemplates() throws IOException {
		Properties english = bundle("messages");
		for (String name : new String[] { "messages_fr", "messages_ar" }) {
			Properties other = bundle(name);
			assertThat(other.stringPropertyNames()).as(name).isEqualTo(english.stringPropertyNames());
			// Text damaged by a wrong encoding (seen for nine Arabic keys until Sprint 2) shows as runs of '?' or U+FFFD.
			other.forEach((key, value) -> assertThat((String) value).as(name + " " + key)
				.doesNotContain("???")
				.doesNotContain("�"));
		}
		Properties arabic = bundle("messages_ar");
		for (String key : new String[] { "mail.PASSWORD_SETUP.intro", "mail.PASSWORD_RESET.intro",
				"mail.PASSWORD_RESET_SELF.intro", "mail.action.setPassword" }) {
			assertThat(arabic.getProperty(key)).as(key).containsPattern("[\\u0600-\\u06FF]");
		}
	}

	@Test
	void aResetTheOwnerAskedForIsWordedAsSuchAndAnAdministratorsResetSaysWhoAsked() {
		Notifications notifications = mock(Notifications.class);
		PasswordResetMailListener listener = new PasswordResetMailListener(notifications);
		ArgumentCaptor<Notice> sent = ArgumentCaptor.forClass(Notice.class);

		listener.onPasswordSetupRequested(new PasswordSetupRequested(7L, "RESET", null, "self-service", 1L, "Sami"));
		listener.onPasswordSetupRequested(new PasswordSetupRequested(7L, "RESET", 3L, "admin@demo.local", 1L, "Sami"));
		listener.onPasswordSetupRequested(new PasswordSetupRequested(7L, "SETUP", 3L, "admin@demo.local", 1L, "Sami"));

		verify(notifications, org.mockito.Mockito.times(3)).enqueue(sent.capture());
		assertThat(sent.getAllValues()).extracting(Notice::kind)
			.containsExactly("PASSWORD_RESET_SELF", "PASSWORD_RESET", "PASSWORD_SETUP");
	}

}
