package com.epi.procurement.platform.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background trigger of the expired-token purge. Exists only when background jobs are on ({@code app.jobs.enabled}, default
 * true): Spring Modulith's moments auto-configuration enables scheduling for the whole application, so a
 * {@code @Scheduled} method on an always-present bean ran even with jobs off (Sprint 2). The work itself stays in
 * {@link ExpiredTokenPurge}, which the QA hook and the tests call directly.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class ExpiredTokenPurgeJob {

	private final ExpiredTokenPurge purge;

	ExpiredTokenPurgeJob(ExpiredTokenPurge purge) {
		this.purge = purge;
	}

	@Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT7M")
	void run() {
		purge.purgeAndLog();
	}

}
