package com.epi.procurement.auditcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.util.ReflectionTestUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditSearch;

import tools.jackson.databind.json.JsonMapper;

/**
 * With background jobs on (every running instance; the integration tests turn them off), Spring Boot defines two
 * executors: the application executor and the task scheduler. The Audit Center must pick the application executor;
 * an unqualified injection stopped the application from starting (found by the Sprint 2 browser run).
 */
class AuditCenterWiringTests {

	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class JobsOn {

	}

	@Test
	void theAuditCenterStartsWithBackgroundJobsOnAndUsesTheApplicationExecutor() {
		new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
			.withUserConfiguration(JobsOn.class)
			.withBean(AuditSearch.class, () -> mock(AuditSearch.class))
			.withBean(AuditLog.class, () -> mock(AuditLog.class))
			.withBean(JsonMapper.class, JsonMapper::new)
			.withBean(AuditCenter.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBeanNamesForType(org.springframework.core.task.AsyncTaskExecutor.class))
					.hasSizeGreaterThan(1);
				assertThat(ReflectionTestUtils.getField(context.getBean(AuditCenter.class), "executor"))
					.isSameAs(context.getBean(AuditCenter.APPLICATION_EXECUTOR));
			});
	}

}
