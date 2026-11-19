package com.epi.procurement.document;

import java.time.Duration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Plugs ClamAV in as the antivirus when {@code APP_CLAMAV_HOST} is set (ADR-013). In a deployed environment the
 * scanner is mandatory: {@code DeploymentConfigurationGuard} refuses to start without it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${app.attachments.clamav.host:}'.isBlank()")
class ClamAvConfiguration {

	@Bean
	ClamAvScanner clamAvScanner(Environment environment) {
		return new ClamAvScanner(environment.getRequiredProperty("app.attachments.clamav.host").trim(),
				environment.getProperty("app.attachments.clamav.port", Integer.class, 3310),
				environment.getProperty("app.attachments.clamav.connect-timeout", Duration.class, Duration.ofSeconds(2)),
				environment.getProperty("app.attachments.clamav.read-timeout", Duration.class, Duration.ofSeconds(30)));
	}

	/** Scanner reachability in the health details (administrators only); a down scanner means uploads are refused. */
	@Bean
	HealthIndicator clamAvHealthIndicator(ClamAvScanner scanner) {
		return () -> scanner.ping() ? Health.up().build()
				: Health.down().withDetail("effect", "uploads are refused until the scanner answers").build();
	}

}
