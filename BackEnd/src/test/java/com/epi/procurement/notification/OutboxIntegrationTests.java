package com.epi.procurement.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Transactional outbox (S2-AC-10, S2DR-10): nothing is sent for a rolled-back transaction, failures are retried
 * with back-off and the stable Message-ID, a success is not sent again; Arabic recipients get right-to-left mail.
 */
class OutboxIntegrationTests extends SqlServerTestSupport {

	@Autowired
	Notifications notifications;

	@Autowired
	DataSource dataSource;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	ObjectProvider<Clock> clocks;

	@Test
	void rolledBackNoticesAreNeverQueuedAndFailuresAreRetried() {
		long leila = userId("budget.tn@demo.local");
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		Notice notice = new Notice("TASK_REMINDER", leila, null, Map.of("number", "TEST-OUTBOX", "stage", "Budget"),
				List.of(), List.of(), "/requests");

		tx.executeWithoutResult(status -> {
			notifications.enqueue(notice);
			status.setRollbackOnly();
		});
		assertThat(rows("TEST-OUTBOX")).isZero();

		tx.executeWithoutResult(status -> notifications.enqueue(notice));
		Map<String, Object> row = jdbc.queryForMap("SELECT TOP (1) id, language, body_html, message_id FROM notification_outbox "
				+ "WHERE subject LIKE '%TEST-OUTBOX%' ORDER BY id DESC");
		long id = ((Number) row.get("id")).longValue();
		// Leila's demo language is Arabic: right-to-left e-mail.
		assertThat(row.get("language")).isEqualTo("ar");
		assertThat((String) row.get("body_html")).contains("dir=\"rtl\"");

		List<String> sentIds = new ArrayList<>();
		OutboxSender failing = new OutboxSender(dataSource, transactionManager, mail -> {
			throw new IllegalStateException("down");
		}, clocks, List.of(), Duration.ofDays(30));
		failing.sendDue();
		assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox WHERE id = ?", Integer.class, id))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, id))
			.isEqualTo("PENDING");

		OutboxSender working = new OutboxSender(dataSource, transactionManager, mail -> {
			if (mail.outboxId() == id) {
				sentIds.add(mail.messageId());
			}
		}, clocks, List.of(), Duration.ofDays(30));
		working.sendDue();
		assertThat(sentIds).as("not due yet (back-off)").isEmpty();
		clock.advance(Duration.ofMinutes(5));
		working.sendDue();
		working.sendDue();
		assertThat(sentIds).containsExactly((String) row.get("message_id"));
		assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, id))
			.isEqualTo("SENT");

		// Retention: sent rows are purged once older than the retention period.
		clock.advance(Duration.ofDays(31));
		working.purge();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE id = ?", Integer.class, id))
			.isZero();
	}

	private int rows(String marker) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE subject LIKE ?", Integer.class,
				"%" + marker + "%");
	}

}
