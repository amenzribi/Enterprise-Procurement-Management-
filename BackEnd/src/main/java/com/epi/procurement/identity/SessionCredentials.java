package com.epi.procurement.identity;

import jakarta.servlet.http.HttpSession;

/**
 * Session-bound credentials epoch (S10-AC-05). Compared to {@code user_account.credentials_version} on every request.
 */
final class SessionCredentials {

	static final String ATTRIBUTE = "EPI_CREDENTIALS_VERSION";

	private SessionCredentials() {
	}

	static void bind(HttpSession session, int credentialsVersion) {
		session.setAttribute(ATTRIBUTE, credentialsVersion);
	}

	static Integer version(HttpSession session) {
		Object value = session.getAttribute(ATTRIBUTE);
		return value instanceof Integer i ? i : null;
	}

}
