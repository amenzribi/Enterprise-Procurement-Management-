package com.epi.procurement.identity;

import java.time.Instant;
import java.util.List;

/**
 * The signed-in user as returned by {@code GET /api/v1/me} and {@code POST /api/v1/auth/login}.
 * {@code sessionExpiresAt} is the earlier of idle and absolute expiry at the time of the response; the client
 * extends the idle part itself on each API call using {@code idleTimeoutSeconds} (no polling - S1DR-11).
 */
record MeResponse(long id, String email, String displayName, Ref company, Ref department, List<String> roles,
		List<String> permissions, String preferredLanguage, Instant sessionExpiresAt, long idleTimeoutSeconds,
		long absoluteTimeoutSeconds, boolean demo) {

	record Ref(long id, String code, String name) {
	}

}
