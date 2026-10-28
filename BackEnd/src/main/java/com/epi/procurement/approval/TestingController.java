package com.epi.procurement.approval;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.notification.OutboxSender;
import com.epi.procurement.platform.time.AdjustableClock;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Test hooks for QA (A1, A2), registered only when {@code app.testing.endpoints=true} (development profile; never
 * in production) and restricted to administrators: run the reminder and e-mail jobs on demand, and shift the
 * adjustable clock.
 */
@RestController
@RequestMapping("/api/v1/testing")
@ConditionalOnProperty(name = "app.testing.endpoints", havingValue = "true")
@org.springframework.context.annotation.Profile("dev")
class TestingController {

	private final ApprovalReminders reminders;

	private final OutboxSender outbox;

	private final CurrentUserProvider currentUser;

	private final Clock clock;

	TestingController(ApprovalReminders reminders, OutboxSender outbox, CurrentUserProvider currentUser,
			org.springframework.beans.factory.ObjectProvider<Clock> clock) {
		this.reminders = reminders;
		this.outbox = outbox;
		this.currentUser = currentUser;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@PostMapping("/jobs/run")
	JobsResult runJobs() {
		Absences.requireAdmin(currentUser.require());
		int sentReminders = reminders.run();
		int sentEmails = outbox.sendDue();
		return new JobsResult(sentReminders, sentEmails);
	}

	@PostMapping("/clock")
	ClockResult shiftClock(@RequestBody ClockBody body) {
		Absences.requireAdmin(currentUser.require());
		if (!(clock instanceof AdjustableClock adjustable)) {
			throw ApiProblemException.conflict("clock-not-adjustable", "Set app.testing.adjustable-clock=true.");
		}
		adjustable.setOffset(Duration.ofMinutes(body.offsetMinutes() == null ? 0 : body.offsetMinutes()));
		return new ClockResult(adjustable.offset().toMinutes(), adjustable.instant().toString());
	}

	record JobsResult(int reminders, int emails) {
	}

	record ClockBody(Long offsetMinutes) {
	}

	record ClockResult(long offsetMinutes, String now) {
	}

}
