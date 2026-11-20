package com.epi.procurement.platform.config;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.springframework.boot.actuate.scheduling.ScheduledTasksEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * The application's scheduled tasks in a context, read through Spring Boot's scheduled-tasks endpoint (the actuator
 * view). {@code Task.getRunnable()} returns a tracking wrapper, so an {@code instanceof ScheduledMethodRunnable} test
 * never matches; the endpoint unwraps it and names the target ({@code com.epi...OutboxJobs.send}).
 */
final class ScheduledApplicationTasks {

	private ScheduledApplicationTasks() {
	}

	/** "SimpleClassName.method" of every task of the application, with its interval (null when not interval-based). */
	static Map<String, Duration> of(ApplicationContext context) {
		ScheduledTasksEndpoint.ScheduledTasksDescriptor tasks = new ScheduledTasksEndpoint(
				context.getBeansOfType(ScheduledTaskHolder.class).values())
			.scheduledTasks();
		Map<String, Duration> found = new TreeMap<>();
		Stream.of(tasks.getFixedDelay(), tasks.getFixedRate(), tasks.getCron(), tasks.getCustom())
			.flatMap(list -> list.stream())
			.forEach(task -> {
				String target = task.getRunnable().getTarget();
				if (target.startsWith("com.epi.procurement.")) {
					String simple = target.substring(target.lastIndexOf('.', target.lastIndexOf('.') - 1) + 1);
					found.put(simple, task instanceof ScheduledTasksEndpoint.IntervalTaskDescriptor interval
							? Duration.ofMillis(interval.getInterval()) : null);
				}
			});
		return found;
	}

	/** Every task in the context, application or not (to show that scheduling itself is on). */
	static long all(ApplicationContext context) {
		ScheduledTasksEndpoint.ScheduledTasksDescriptor tasks = new ScheduledTasksEndpoint(
				context.getBeansOfType(ScheduledTaskHolder.class).values())
			.scheduledTasks();
		return Stream.of(tasks.getFixedDelay(), tasks.getFixedRate(), tasks.getCron(), tasks.getCustom())
			.mapToLong(list -> list.size())
			.sum();
	}

}
