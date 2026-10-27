package com.epi.procurement.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * System UTC time plus an adjustable offset, so tests can move time forward (session expiry, throttling, a new
 * year) without waiting. Always {@link #reset()} after use: the clock is shared by the whole test context.
 */
public final class MutableClock extends Clock {

	private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

	public void advance(Duration duration) {
		offset.updateAndGet(current -> current.plus(duration));
	}

	public void setOffset(Duration duration) {
		offset.set(duration);
	}

	public void reset() {
		offset.set(Duration.ZERO);
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

}
