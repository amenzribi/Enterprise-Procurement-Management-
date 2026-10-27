package com.epi.procurement.identity;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Enforces the idle and absolute session timeouts with the application clock (S1-AC-01, S1DR-12), so both are
 * testable and independent of when the session object was created. The container timeout is only a backstop.
 * <p>
 * An expired session is invalidated. Sign-in endpoints and health still proceed (anonymously); every other request
 * is answered with 401 {@code session-expired}.
 */
final class SessionTimeouts extends OncePerRequestFilter {

	static final String AUTHENTICATED_AT = SessionTimeouts.class.getName() + ".authenticatedAt";

	static final String LAST_ACTIVITY = SessionTimeouts.class.getName() + ".lastActivity";

	private final SecuritySettings.Session settings;

	private final Clock clock;

	SessionTimeouts(SecuritySettings.Session settings, Clock clock) {
		this.settings = settings;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		Instant authenticatedAt = session == null ? null : (Instant) session.getAttribute(AUTHENTICATED_AT);
		if (authenticatedAt != null) {
			Instant now = clock.instant();
			if (!now.isBefore(expiresAt(session, settings))) {
				session.invalidate();
				SecurityContextHolder.clearContext();
				if (!isAnonymousEndpoint(request)) {
					ProblemResponses.unauthenticated(response, true);
					return;
				}
			}
			else {
				session.setAttribute(LAST_ACTIVITY, now);
			}
		}
		chain.doFilter(request, response);
	}

	/**
	 * The earlier of idle expiry (last activity + idle timeout) and absolute expiry (sign-in + absolute timeout).
	 */
	static Instant expiresAt(HttpSession session, SecuritySettings.Session settings) {
		Instant authenticatedAt = (Instant) session.getAttribute(AUTHENTICATED_AT);
		Instant lastActivity = (Instant) session.getAttribute(LAST_ACTIVITY);
		Instant idle = (lastActivity == null ? authenticatedAt : lastActivity).plus(settings.idleTimeout());
		Instant absolute = authenticatedAt.plus(settings.absoluteTimeout());
		return idle.isBefore(absolute) ? idle : absolute;
	}

	static void start(HttpSession session, Instant now, SecuritySettings.Session settings) {
		session.setAttribute(AUTHENTICATED_AT, now);
		session.setAttribute(LAST_ACTIVITY, now);
		// Container backstop slightly beyond the application idle timeout.
		session.setMaxInactiveInterval((int) settings.idleTimeout().plusMinutes(1).toSeconds());
	}

	private static boolean isAnonymousEndpoint(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.startsWith("/api/v1/auth/") || path.equals("/actuator/health");
	}

}
