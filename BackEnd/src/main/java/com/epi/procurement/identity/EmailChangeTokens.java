package com.epi.procurement.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates the proof-of-control token of an e-mail change when its e-mail is sent, so the secret never sits in the
 * outbox (same deferred-secret pattern as password links). Only the SHA-256 hash is stored; a new token replaces the
 * previous one of the same change.
 */
@Component
public class EmailChangeTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final EmailChangeStore changes;

	private final Duration ttl;

	private final Clock clock;

	EmailChangeTokens(EmailChangeStore changes, @Value("${app.security.password-reset-ttl:PT24H}") Duration ttl,
			ObjectProvider<Clock> clock) {
		this.changes = changes;
		this.ttl = ttl;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/** The SPA path {@code /e#token} for a change waiting for proof of control, or the sign-in page if it is not. */
	public String createLinkPath(long changeId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return changes.setToken(changeId, PasswordResetTokens.hash(token), clock.instant().plus(ttl)) ? "/e#" + token
				: "/sign-in";
	}

}
