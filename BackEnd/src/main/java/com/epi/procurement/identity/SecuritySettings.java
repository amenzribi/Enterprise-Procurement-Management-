package com.epi.procurement.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security parameters ({@code app.security.*}). Defaults are the production values from Sprint 1 (S1-AC-01,
 * S1DR-03); tests shorten them (QA assumptions T-1, T-2). Trusted proxies for rate limits are configured as
 * {@code app.security.trusted-proxies} (comma-separated) and read by {@code ClientAddressResolver}.
 */
@ConfigurationProperties("app.security")
record SecuritySettings(@DefaultValue Session session, @DefaultValue Login login,
		@DefaultValue("true") boolean cookieSecure) {

	/**
	 * @param idleTimeout sign-out after this much inactivity
	 * @param absoluteTimeout sign-out this long after sign-in, whatever the activity
	 */
	record Session(@DefaultValue("30m") Duration idleTimeout, @DefaultValue("8h") Duration absoluteTimeout) {
	}

	/**
	 * Sign-in throttling: failures are counted per (e-mail, client address) and per client address within
	 * {@code window}; reaching a limit blocks further attempts for {@code blockDuration}.
	 */
	record Login(@DefaultValue("5") int maxFailuresPerAccount, @DefaultValue("50") int maxFailuresPerAddress,
			@DefaultValue("15m") Duration window, @DefaultValue("15m") Duration blockDuration) {
	}

}
