package com.epi.procurement.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background trigger of the outbox (sending and purging e-mails). Exists only when background jobs are on ({@code app.jobs.enabled}, default
 * true): Spring Modulith's moments auto-configuration enables scheduling for the whole application, so a
 * {@code @Scheduled} method on an always-present bean ran even with jobs off (Sprint 2). The work itself stays in
 * {@link OutboxSender}, which the QA hook and the tests call directly.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class OutboxJobs {

	private final OutboxSender sender;

	OutboxJobs(OutboxSender sender) {
		this.sender = sender;
	}

	@Scheduled(fixedDelayString = "${app.jobs.outbox-delay:PT15S}", initialDelayString = "PT10S")
	void send() {
		sender.sendDue();
	}

	@Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT2M")
	void purge() {
		sender.purge();
	}

}
