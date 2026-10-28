package com.epi.procurement.platform.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * System UTC time plus an adjustable offset, for manual QA of SLAs, reminders and link expiry (test hook A1). Only
 * registered as the application clock when {@code app.testing.adjustable-clock=true} (never in production
 * profiles); the offset is changed through the testing endpoint.
 */
public final class AdjustableClock extends Clock {

	private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

	public void setOffset(Duration duration) {
		offset.set(duration);
	}

	public Duration offset() {
		return offset.get();
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(offset.get());
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("Use LocalDate.ofInstant(clock.instant(), zone)");
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "app.testing.adjustable-clock", havingValue = "true")
	static class AdjustableClockConfiguration {

		@Bean
		AdjustableClock adjustableClock() {
			return new AdjustableClock();
		}

	}

}
