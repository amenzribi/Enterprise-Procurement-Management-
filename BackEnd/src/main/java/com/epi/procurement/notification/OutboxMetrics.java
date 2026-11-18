package com.epi.procurement.notification;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * E-mail outbox figures for monitoring (audit D-5): stuck e-mail is the first thing users notice. Read on demand,
 * when the metrics endpoint is queried.
 * <ul>
 * <li>{@code procurement.outbox.backlog}: e-mails waiting to be sent;</li>
 * <li>{@code procurement.outbox.oldest.seconds}: age of the oldest waiting e-mail;</li>
 * <li>{@code procurement.outbox.failed}: e-mails given up after every retry.</li>
 * </ul>
 */
@Component
class OutboxMetrics {

	private final JdbcTemplate jdbc;

	private final ObjectProvider<MeterRegistry> registry;

	OutboxMetrics(DataSource dataSource, ObjectProvider<MeterRegistry> registry) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.registry = registry;
	}

	/** Registered once the application is ready, so the gauges never take part in start-up wiring. */
	@EventListener(ApplicationReadyEvent.class)
	void register() {
		registry.ifAvailable(meters -> {
			Gauge.builder("procurement.outbox.backlog", this, OutboxMetrics::backlog)
				.description("E-mails waiting to be sent")
				.register(meters);
			Gauge.builder("procurement.outbox.oldest.seconds", this, OutboxMetrics::oldestSeconds)
				.description("Age of the oldest e-mail waiting to be sent")
				.baseUnit("seconds")
				.register(meters);
			Gauge.builder("procurement.outbox.failed", this, OutboxMetrics::failed)
				.description("E-mails abandoned after every retry")
				.register(meters);
		});
	}

	double backlog() {
		return count("SELECT COUNT(*) FROM notification_outbox WHERE status IN ('PENDING', 'SENDING')");
	}

	double failed() {
		return count("SELECT COUNT(*) FROM notification_outbox WHERE status = 'FAILED'");
	}

	double oldestSeconds() {
		return count("SELECT COALESCE(DATEDIFF(SECOND, MIN(created_at), SYSUTCDATETIME()), 0) FROM notification_outbox "
				+ "WHERE status IN ('PENDING', 'SENDING')");
	}

	private double count(String sql) {
		try {
			Number value = jdbc.queryForObject(sql, Number.class);
			return value == null ? 0 : value.doubleValue();
		}
		catch (RuntimeException ex) {
			// A gauge must not fail the metrics endpoint when the database is down; health reports that.
			return Double.NaN;
		}
	}

}
