package com.epi.procurement.platform.web;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request an id and writes one access line when it ends (audit A-2, P-4).
 * <ul>
 * <li>The id is generated here, never taken from the client, returned in {@code X-Request-Id}, placed in the logging
 * context ({@code requestId}) for every line written while the request runs, and never written into a body, so answers that must look alike stay identical. A user
 * who reports an error quotes the id; support finds the lines.</li>
 * <li>The access line holds the method, the path, the status, the duration and the user id. It never holds the
 * query string, headers, cookies or bodies: search text, tokens and personal data stay out of the log.</li>
 * </ul>
 * Runs before the security filters, so refused requests (401, 403, rate limits) are logged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	/** Logging-context keys. {@code userId} is set by the identity module once the session is verified. */
	public static final String MDC_REQUEST_ID = "requestId";

	public static final String MDC_USER_ID = "userId";

	private static final Logger access = LoggerFactory.getLogger("com.epi.procurement.access");

	private static final SecureRandom RANDOM = new SecureRandom();

	/** The id of the request being handled on this thread, or null outside a request. */
	public static String currentRequestId() {
		return MDC.get(MDC_REQUEST_ID);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		byte[] bytes = new byte[8];
		RANDOM.nextBytes(bytes);
		String id = HexFormat.of().formatHex(bytes);
		MDC.put(MDC_REQUEST_ID, id);
		response.setHeader(HEADER, id);
		long started = System.nanoTime();
		boolean failed = true;
		try {
			chain.doFilter(request, response);
			failed = false;
		}
		finally {
			long millis = (System.nanoTime() - started) / 1_000_000;
			// An exception that escapes every handler is answered 500 by the container after this filter.
			int status = failed ? 500 : response.getStatus();
			String user = MDC.get(MDC_USER_ID);
			String path = request.getRequestURI();
			if (path.startsWith("/actuator/health")) {
				access.debug("{} {} {} {}ms user={}", request.getMethod(), path, status, millis, user);
			}
			else if (status >= 500) {
				access.warn("{} {} {} {}ms user={}", request.getMethod(), path, status, millis, user);
			}
			else {
				access.info("{} {} {} {}ms user={}", request.getMethod(), path, status, millis, user);
			}
			MDC.remove(MDC_USER_ID);
			MDC.remove(MDC_REQUEST_ID);
		}
	}

}
