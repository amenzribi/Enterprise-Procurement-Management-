package com.epi.procurement.platform.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding fixed-window limiter for public link endpoints (S9-AC-02). Keys are opaque (address and/or
 * token fingerprint) — never log raw tokens.
 */
@Component
public class RequestRateLimiter {

	private final Clock clock;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public RequestRateLimiter(ObjectProvider<Clock> clock) {
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	public void check(String bucketKey, int limitPerMinute) {
		if (limitPerMinute < 1) {
			return;
		}
		Instant now = clock.instant();
		if (windows.size() > 20_000) {
			windows.entrySet().removeIf(e -> e.getValue().start().plus(Duration.ofMinutes(1)).isBefore(now));
		}
		Window window = windows.compute(bucketKey, (key, current) -> current == null
				|| current.start().plus(Duration.ofMinutes(1)).isBefore(now) ? new Window(now, 1)
						: new Window(current.start(), current.count() + 1));
		if (window.count() > limitPerMinute) {
			throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
					"Too many attempts. Please wait a minute.");
		}
	}

	private record Window(Instant start, int count) {
	}

}
