package com.epi.procurement.identity;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JSON answers instead of redirects or browser authentication pop-ups (S1DR-13). A request carrying a session id
 * that is no longer valid gets {@code session-expired}; note that a server restart also expires every session
 * (in-memory sessions, single node - ADR-003).
 */
final class ApiSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		boolean expired = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
		ProblemResponses.unauthenticated(response, expired);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		if (accessDeniedException instanceof CsrfException) {
			ProblemResponses.write(response, 403, "Forbidden", "csrf-invalid",
					"The security token is missing or invalid. Reload the page and try again.");
		}
		else {
			ProblemResponses.write(response, 403, "Forbidden", "forbidden", "You are not allowed to perform this action.");
		}
	}

}
