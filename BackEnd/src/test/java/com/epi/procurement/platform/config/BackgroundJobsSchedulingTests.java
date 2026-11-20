package com.epi.procurement.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Sprint 2 regression: {@code app.jobs.enabled=false} turns off every application job even though Spring Modulith's
 * moments auto-configuration enables scheduling for the whole application (it carries {@code @EnableScheduling}). With
 * the flag on, or absent, the same jobs are scheduled with their production intervals.
 */
class BackgroundJobsSchedulingTests {

	/** The job triggers and the work they delegate to (mocked here: nothing runs against a database). */
	private static final String[][] JOBS = {
			{ "com.epi.procurement.notification.OutboxJobs", "com.epi.procurement.notification.OutboxSender" },
			{ "com.epi.procurement.approval.ApprovalReminderJob", "com.epi.procurement.approval.ApprovalReminders" },
			{ "com.epi.procurement.platform.persistence.ExpiredTokenPurgeJob",
					"com.epi.procurement.platform.persistence.ExpiredTokenPurge" },
			{ "com.epi.procurement.messaging.InternalMessageStreamHeartbeat",
					"com.epi.procurement.messaging.InternalMessageStream" },
			{ "com.epi.procurement.platform.web.IdempotencyPurgeJob", "com.epi.procurement.platform.web.Idempotency" } };

	private static final Map<String, Duration> EXPECTED = new TreeMap<>(Map.of(
			"OutboxJobs.send", Duration.ofSeconds(15),
			"OutboxJobs.purge", Duration.ofHours(6),
			"ApprovalReminderJob.run", Duration.ofMinutes(1),
			"ExpiredTokenPurgeJob.run", Duration.ofHours(6),
			"InternalMessageStreamHeartbeat.beat", Duration.ofSeconds(25),
			"IdempotencyPurgeJob.run", Duration.ofHours(1)));

	private static ApplicationContextRunner runner() throws ClassNotFoundException {
		// The real Spring Modulith auto-configuration (not public, hence by name): it is what enables scheduling.
		Class<?> moments = Class.forName("org.springframework.modulith.moments.autoconfigure.MomentsAutoConfiguration");
		ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(moments, TaskSchedulingAutoConfiguration.class));
		for (String[] job : JOBS) {
			Class<?> work = Class.forName(job[1]);
			runner = register(runner, work);
			runner = runner.withBean(Class.forName(job[0]));
		}
		return runner;
	}

	@SuppressWarnings("unchecked")
	private static <T> ApplicationContextRunner register(ApplicationContextRunner runner, Class<T> type) {
		return runner.withBean(type, () -> mock(type));
	}

	@Test
	void withJobsOffNoApplicationJobIsScheduledAlthoughModulithEnablesScheduling() throws Exception {
		runner().withPropertyValues("app.jobs.enabled=false").run(context -> {
			assertThat(context).hasNotFailed();
			// Scheduling itself is on (moments schedules its own hourly and daily tasks): what let the jobs run before.
			assertThat(ScheduledApplicationTasks.all(context)).isPositive();
			for (String[] job : JOBS) {
				assertThat(context.getBeanNamesForType(Class.forName(job[0]))).as(job[0]).isEmpty();
			}
			assertThat(ScheduledApplicationTasks.of(context)).isEmpty();
		});
	}

	@Test
	void withJobsOnEveryJobIsScheduledWithItsInterval() throws Exception {
		runner().withPropertyValues("app.jobs.enabled=true")
			.run(context -> assertThat(ScheduledApplicationTasks.of(context)).isEqualTo(EXPECTED));
	}

	@Test
	void jobsAreOnByDefault() throws Exception {
		runner().run(context -> assertThat(ScheduledApplicationTasks.of(context)).isEqualTo(EXPECTED));
	}

}
