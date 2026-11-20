package com.epi.procurement.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 2 regression, in the real application context of the test suite: the tests run with
 * {@code app.jobs.enabled=false}, and no application job is scheduled there. Before the fix the scheduled outbox
 * sender ran during the suite (scheduling enabled by Spring Modulith) and raced the tests that send e-mails
 * explicitly. Shares the suite's context (no property override).
 */
class BackgroundJobsIntegrationTests extends SqlServerTestSupport {

	@Autowired
	ApplicationContext context;

	@Test
	void theTestSuiteRunsWithoutAnyScheduledApplicationJob() {
		assertThat(context.getEnvironment().getProperty("app.jobs.enabled")).isEqualTo("false");
		// Scheduling is on in this context (Spring Modulith schedules its own tasks), yet no application job is.
		assertThat(ScheduledApplicationTasks.all(context)).isPositive();
		assertThat(ScheduledApplicationTasks.of(context)).isEmpty();
	}

}
