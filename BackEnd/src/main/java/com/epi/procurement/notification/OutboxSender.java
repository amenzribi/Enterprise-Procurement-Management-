package com.epi.procurement.notification;

import com.epi.procurement.platform.persistence.UtcTimes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends pending outbox e-mails, at least once (S2-AC-10, S2CR-06):
 * <ol>
 * <li>claim a batch ({@code UPDLOCK, READPAST}: concurrent senders never take the same row), mark it SENDING and
 * commit - no database transaction is open during the SMTP call;</li>
 * <li>resolve deferred links (one-tap tokens are created now, never stored in the outbox, S2CR-01) and send;</li>
 * <li>mark each row SENT, or back off (1, 2, 4 ... 60 minutes) and stop after {@value #MAX_ATTEMPTS} attempts.</li>
 * </ol>
 * A crash between claim and mark leaves the row SENDING; it is claimed again after {@value #STALE_CLAIM_MINUTES}
 * minutes and re-sent with the same Message-ID. Sent and failed rows are purged after {@code app.mail.retention}.
 */
@Component
public class OutboxSender {

	static final int MAX_ATTEMPTS = 8;

	static final int STALE_CLAIM_MINUTES = 5;

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{link:([a-z]+)((?::[0-9]+)*)\\}\\}");

	private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final MailTransport transport;

	private final List<NoticeLinkResolver> resolvers;

	private final Clock clock;

	private final Duration retention;

	@Autowired
	OutboxSender(DataSource dataSource, PlatformTransactionManager transactionManager, MailTransport transport,
			ObjectProvider<Clock> clock, List<NoticeLinkResolver> resolvers,
			@Value("${app.mail.retention:P30D}") Duration retention) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.transaction = new TransactionTemplate(transactionManager);
		this.transport = transport;
		this.resolvers = List.copyOf(resolvers);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.retention = retention;
	}

	@Scheduled(fixedDelayString = "${app.jobs.outbox-delay:PT15S}", initialDelayString = "PT10S")
	void scheduled() {
		sendDue();
	}

	@Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT2M")
	void scheduledPurge() {
		purge();
	}

	/**
	 * Sends every due e-mail; returns the number sent.
	 */
	public int sendDue() {
		int sent = 0;
		for (int batch = 0; batch < 50; batch++) {
			List<MailTransport.OutgoingMail> claimed = transaction.execute(status -> claim());
			if (claimed == null || claimed.isEmpty()) {
				break;
			}
			for (MailTransport.OutgoingMail mail : claimed) {
				if (send(mail)) {
					sent++;
				}
			}
		}
		return sent;
	}

	/**
	 * Deletes sent and failed rows older than the retention period; returns the number deleted.
	 */
	public int purge() {
		return jdbc.update("DELETE FROM notification_outbox WHERE status IN ('SENT', 'FAILED') AND created_at < ?",
				UtcTimes.db(clock.instant().minus(retention)));
	}

	private List<MailTransport.OutgoingMail> claim() {
		Instant now = clock.instant();
		List<MailTransport.OutgoingMail> due = jdbc.query("""
				SELECT TOP (20) id, message_id, recipient_email, subject, body_html, body_text
				FROM notification_outbox WITH (UPDLOCK, READPAST, ROWLOCK)
				WHERE (status = 'PENDING' AND next_attempt_at <= ?) OR (status = 'SENDING' AND claimed_at <= ?)
				ORDER BY id""",
				(rs, i) -> new MailTransport.OutgoingMail(rs.getLong("id"), rs.getString("message_id"),
						rs.getString("recipient_email"), rs.getString("subject"), rs.getString("body_html"),
						rs.getString("body_text")), UtcTimes.db(now),
				UtcTimes.db(now.minus(Duration.ofMinutes(STALE_CLAIM_MINUTES))));
		for (MailTransport.OutgoingMail mail : due) {
			jdbc.update("UPDATE notification_outbox SET status = 'SENDING', claimed_at = ? WHERE id = ?", UtcTimes.db(now),
					mail.outboxId());
		}
		return due;
	}

	private boolean send(MailTransport.OutgoingMail mail) {
		Instant now = clock.instant();
		try {
			// One resolution per placeholder per e-mail: the HTML and text parts carry the same token (QA2-D-08).
			java.util.Map<String, String> resolved = new java.util.HashMap<>();
			transport.send(new MailTransport.OutgoingMail(mail.outboxId(), mail.messageId(), mail.to(), mail.subject(),
					resolve(mail.html(), resolved), resolve(mail.text(), resolved)));
			jdbc.update("""
					UPDATE notification_outbox SET status = 'SENT', sent_at = ?, attempts = attempts + 1, claimed_at = NULL
					WHERE id = ? AND status = 'SENDING'""", UtcTimes.db(now), mail.outboxId());
			return true;
		}
		catch (Exception ex) {
			Integer attempts = jdbc.queryForObject("SELECT attempts FROM notification_outbox WHERE id = ?", Integer.class,
					mail.outboxId());
			int next = (attempts == null ? 0 : attempts) + 1;
			long backoffMinutes = Math.min(60, 1L << Math.min(next - 1, 6));
			jdbc.update("""
					UPDATE notification_outbox SET attempts = ?, status = ?, next_attempt_at = ?, last_error = ?, claimed_at = NULL
					WHERE id = ?""", next, next >= MAX_ATTEMPTS ? "FAILED" : "PENDING",
					UtcTimes.db(now.plus(Duration.ofMinutes(backoffMinutes))), ex.getClass().getSimpleName(),
					mail.outboxId());
			log.warn("E-mail {} not sent (attempt {}): {}", mail.outboxId(), next, ex.getClass().getSimpleName());
			return false;
		}
	}

	/**
	 * Replaces deferred link placeholders; an unresolved link falls back to the application's home page.
	 */
	private String resolve(String body, java.util.Map<String, String> resolved) {
		Matcher matcher = PLACEHOLDER.matcher(body);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String kind = matcher.group(1);
			List<String> args = matcher.group(2).isEmpty() ? List.of()
					: Arrays.asList(matcher.group(2).substring(1).split(":"));
			String path = resolved.computeIfAbsent(matcher.group(), placeholder -> resolvers.stream()
				.map(r -> r.resolve(kind, args))
				.flatMap(Optional::stream)
				.findFirst()
				.orElse("/"));
			matcher.appendReplacement(result, Matcher.quoteReplacement(path));
		}
		matcher.appendTail(result);
		return result.toString();
	}

}
