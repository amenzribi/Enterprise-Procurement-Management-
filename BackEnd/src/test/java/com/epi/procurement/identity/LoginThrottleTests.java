package com.epi.procurement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.MutableClock;

class LoginThrottleTests {

	private final MutableClock clock = new MutableClock();

	private final LoginThrottle throttle = new LoginThrottle(
			new SecuritySettings.Login(5, 50, Duration.ofMinutes(15), Duration.ofMinutes(15)), clock);

	/**
	 * S1CR-01: a spray of distinct e-mails and addresses cannot grow the map without bound.
	 */
	@Test
	void theCounterMapStaysBounded() {
		for (int i = 0; i < 30_000; i++) {
			throttle.recordFailure("spray" + i + "@example.com", "10.1." + (i / 250) + "." + (i % 250));
		}

		assertThat(throttle.size()).isLessThanOrEqualTo(LoginThrottle.MAX_ENTRIES);
	}

	@Test
	void expiredCountersAreSwept() {
		for (int i = 0; i < 400; i++) {
			throttle.recordFailure("old" + i + "@example.com", "10.2.0." + (i % 250));
		}
		clock.advance(Duration.ofMinutes(31));
		for (int i = 0; i < 600; i++) {
			throttle.recordFailure("new@example.com", "10.3.0.1");
		}

		// Only the recent pair and address remain after the periodic sweep.
		assertThat(throttle.size()).isLessThanOrEqualTo(2);
	}

}
