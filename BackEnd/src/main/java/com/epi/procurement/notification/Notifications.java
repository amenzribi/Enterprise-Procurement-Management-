package com.epi.procurement.notification;

import com.epi.procurement.platform.persistence.UtcTimes;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.identity.UserDirectory;

/**
 * Enqueues notifications: one outbox e-mail and one in-app notification per recipient, written in the caller's
 * transaction so they exist exactly when the business change commits (S2-AC-10).
 */
@Service
public class Notifications {

	private final JdbcTemplate jdbc;

	private final UserDirectory users;

	private final NotificationTemplates templates;

	private final Clock clock;

	private final TransactionTemplate independent;

	private final String messageIdDomain;

	Notifications(DataSource dataSource, UserDirectory users, NotificationTemplates templates,
			ObjectProvider<Clock> clock, PlatformTransactionManager transactionManager,
			@Value("${app.mail.message-id-domain:procurement.local}") String messageIdDomain) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.users = users;
		this.templates = templates;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.independent = new TransactionTemplate(transactionManager);
		this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.messageIdDomain = messageIdDomain;
	}

	/**
	 * Enqueues in the current transaction. Inactive or unknown recipients are skipped.
	 */
	public void enqueue(Notice notice) {
		Optional<UserDirectory.UserContact> contact = users.findContact(notice.recipientUserId());
		if (contact.isEmpty() || !contact.get().active()) {
			return;
		}
		UserDirectory.UserContact recipient = contact.get();
		Locale locale = Locale.forLanguageTag(recipient.preferredLanguage());
		NotificationTemplates.Rendered rendered = templates.render(notice, locale);
		Instant now = clock.instant();

		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_notification_outbox", Long.class);
		jdbc.update("""
				INSERT INTO notification_outbox (id, kind, recipient_user_id, recipient_email, language, subject, body_html,
				                                 body_text, message_id, status, next_attempt_at, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)""", id, notice.kind(), recipient.id(),
				recipient.email(), recipient.preferredLanguage(), rendered.subject(), rendered.html(), rendered.text(),
				"<notification-" + id + "@" + messageIdDomain + ">", UtcTimes.db(now), UtcTimes.db(now));
		jdbc.update("""
				INSERT INTO in_app_notification (user_id, kind, request_id, title, body, link, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", recipient.id(), notice.kind(), notice.requestId(),
				truncate(rendered.subject(), 300), truncate(rendered.inAppBody(), 1000), notice.inAppLink(),
				UtcTimes.db(now));
	}

	/**
	 * Enqueues an e-mail to an address that is not (or no longer) the account's (Sprint 2, e-mail correction): the
	 * proof-of-control link goes to the NEW address, the security notice to the OLD one. Language from the account,
	 * whether or not it is active; no in-app notification. In the current transaction.
	 */
	public void enqueueToAddress(Notice notice, String email) {
		Optional<UserDirectory.UserContact> contact = users.findContact(notice.recipientUserId());
		if (contact.isEmpty() || email == null || email.isBlank()) {
			return;
		}
		UserDirectory.UserContact recipient = contact.get();
		NotificationTemplates.Rendered rendered = templates.render(notice, Locale.forLanguageTag(recipient.preferredLanguage()));
		Instant now = clock.instant();
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_notification_outbox", Long.class);
		jdbc.update("""
				INSERT INTO notification_outbox (id, kind, recipient_user_id, recipient_email, language, subject, body_html,
				                                 body_text, message_id, status, next_attempt_at, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)""", id, notice.kind(), recipient.id(), email,
				recipient.preferredLanguage(), rendered.subject(), rendered.html(), rendered.text(),
				"<notification-" + id + "@" + messageIdDomain + ">", UtcTimes.db(now), UtcTimes.db(now));
	}

	/**
	 * Enqueues in a separate transaction that commits even when the caller's transaction rolls back (e.g. telling
	 * administrators that a submission failed because a stage has no eligible approver).
	 */
	public void enqueueIndependently(Notice notice) {
		independent.executeWithoutResult(status -> enqueue(notice));
	}

	private static String truncate(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

}
