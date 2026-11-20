package com.epi.procurement.platform.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background trigger of the idempotency-record purge. Exists only when background jobs are on
 * ({@code app.jobs.enabled}, default true): Spring Modulith's moments auto-configuration enables scheduling for the
 * whole application, so a {@code @Scheduled} method on an always-present bean ran even with jobs off (Sprint 2). The
 * work itself stays in {@link Idempotency}.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class IdempotencyPurgeJob {

	private final Idempotency idempotency;

	IdempotencyPurgeJob(Idempotency idempotency) {
		this.idempotency = idempotency;
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
	void run() {
		idempotency.purge();
	}

}
