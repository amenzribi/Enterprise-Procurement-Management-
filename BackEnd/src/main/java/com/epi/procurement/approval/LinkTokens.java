package com.epi.procurement.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One-tap approval tokens (S2-AC-07, S2DR-07): 256 random bits, URL-safe, single use, bound to one task and one
 * approver, valid {@code app.approval.link-ttl} (default 72 h). Only the SHA-256 is stored. The token travels in the
 * URL fragment ({@code /a#token}), which browsers never send to servers or in Referer headers.
 */
@Component
class LinkTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ApprovalStore store;

	private final Duration ttl;

	LinkTokens(ApprovalStore store, @Value("${app.approval.link-ttl:PT72H}") Duration ttl) {
		this.store = store;
		this.ttl = ttl;
	}

	/**
	 * Creates a token for the task's assignee and returns the application path to put in the e-mail.
	 */
	String create(long taskId, long userId, Instant now) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		store.insertToken(hash(token), taskId, userId, now.plus(ttl), now);
		return "/a#" + token;
	}

	static String hash(String token) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
