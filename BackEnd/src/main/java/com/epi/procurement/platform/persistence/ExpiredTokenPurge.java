package com.epi.procurement.platform.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes link tokens long after they expired (audit DB-4). A token row holds only a hash, its owner and its dates;
 * once expired it can no longer be redeemed, and what it did is in the audit trail. Rows are kept for the retention
 * period after expiry (90 days by default, {@code app.retention.expired-tokens}) to investigate a recent incident,
 * then deleted in small batches so the job never holds a long lock.
 * <p>
 * The tables belong to three modules; this job only deletes by expiry date and knows nothing else about them.
 */
@Component
public class ExpiredTokenPurge {

	private static final Logger log = LoggerFactory.getLogger(ExpiredTokenPurge.class);

	/** Fixed names, never user input. None of these tables is referenced by a foreign key. */
	private static final List<String> TABLES = List.of("approval_link_token", "quote_link_token",
			"password_reset_token");

	private static final int BATCH = 1000;

	private static final int MAX_BATCHES = 50;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final Duration retention;

	ExpiredTokenPurge(DataSource dataSource, ObjectProvider<Clock> clock,
			@Value("${app.retention.expired-tokens:P90D}") Duration retention) {
		if (retention.isNegative() || retention.compareTo(Duration.ofDays(1)) < 0) {
			throw new IllegalStateException("app.retention.expired-tokens must be at least one day");
		}
		this.jdbc = new JdbcTemplate(dataSource);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.retention = retention;
	}

	@Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT7M")
	void scheduled() {
		Map<String, Integer> deleted = purge();
		if (deleted.values().stream().anyMatch(count -> count > 0)) {
			log.info("Expired tokens purged: {}", deleted);
		}
	}

	/** Deletes tokens that expired more than the retention period ago; returns the number deleted per table. */
	public Map<String, Integer> purge() {
		Instant before = clock.instant().minus(retention);
		Map<String, Integer> deleted = new LinkedHashMap<>();
		for (String table : TABLES) {
			int total = 0;
			for (int batch = 0; batch < MAX_BATCHES; batch++) {
				int rows = jdbc.update("DELETE TOP (" + BATCH + ") FROM " + table + " WHERE expires_at < ?",
						UtcTimes.db(before));
				total += rows;
				if (rows < BATCH) {
					break;
				}
			}
			deleted.put(table, total);
		}
		return deleted;
	}

}
