package com.epi.procurement.approval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background trigger of approval reminders and escalations. Exists only when background jobs are on ({@code app.jobs.enabled}, default
 * true): Spring Modulith's moments auto-configuration enables scheduling for the whole application, so a
 * {@code @Scheduled} method on an always-present bean ran even with jobs off (Sprint 2). The work itself stays in
 * {@link ApprovalReminders}, which the QA hook and the tests call directly.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class ApprovalReminderJob {

	private final ApprovalReminders reminders;

	ApprovalReminderJob(ApprovalReminders reminders) {
		this.reminders = reminders;
	}

	@Scheduled(fixedDelayString = "${app.jobs.reminder-delay:PT1M}", initialDelayString = "PT30S")
	void run() {
		reminders.run();
	}

}
