package com.epi.procurement.identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes RFC 9457 problem details from security filters, which run outside Spring MVC's exception handling.
 */
final class ProblemResponses {

	private ProblemResponses() {
	}

	static void write(HttpServletResponse response, int status, String title, String code, String detail)
			throws IOException {
		response.setStatus(status);
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter()
			.write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + ",\"detail\":\""
					+ detail + "\",\"code\":\"" + code + "\"}");
	}

	static void unauthenticated(HttpServletResponse response, boolean expired) throws IOException {
		if (expired) {
			write(response, 401, "Unauthorized", "session-expired", "Your session has expired. Please sign in again.");
		}
		else {
			write(response, 401, "Unauthorized", "unauthenticated", "Authentication is required.");
		}
	}

}
