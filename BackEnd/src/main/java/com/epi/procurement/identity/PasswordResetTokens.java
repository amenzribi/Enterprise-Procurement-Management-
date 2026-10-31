package com.epi.procurement.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates password setup/reset tokens (S10-AC-04). Only SHA-256 is stored.
 */
@Component
public class PasswordResetTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final PasswordResetStore store;

	private final Duration ttl;

	private final Clock clock;

	PasswordResetTokens(PasswordResetStore store, @Value("${app.security.password-reset-ttl:PT24H}") Duration ttl,
			ObjectProvider<Clock> clock) {
		this.store = store;
		this.ttl = ttl;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/** Creates a token and returns the SPA path {@code /p#token}. */
	public String createLinkPath(long userId, String purpose, Long createdBy) {
		Instant now = clock.instant();
		store.expireActive(userId, now);
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		store.insert(userId, hash(token), purpose, now.plus(ttl), now, createdBy);
		return "/p#" + token;
	}

	public static String hash(String token) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
