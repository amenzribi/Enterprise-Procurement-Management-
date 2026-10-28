package com.epi.procurement.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background jobs (outbox e-mails, approval reminders). Disabled with {@code app.jobs.enabled=false}, e.g. in the
 * integration tests, which run the jobs explicitly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration {

}
