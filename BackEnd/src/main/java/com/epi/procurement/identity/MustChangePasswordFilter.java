package com.epi.procurement.identity;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * When {@code must_change_password} is set, only identity bootstrap endpoints remain available (S10-AC-10).
 */
final class MustChangePasswordFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Object cached = request.getAttribute(CurrentUserProvider.CACHE_ATTRIBUTE);
		if (cached instanceof CurrentUser user && user.mustChangePassword() && !allowed(request)) {
			ProblemResponses.write(response, 403, "Forbidden", "password-change-required",
					"You must change your password before continuing.");
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean allowed(HttpServletRequest request) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if (HttpMethod.GET.matches(method) && (path.equals("/api/v1/me") || path.equals("/api/v1/auth/csrf"))) {
			return true;
		}
		if (HttpMethod.POST.matches(method) && (path.equals("/api/v1/auth/logout")
				|| path.equals("/api/v1/auth/password") || path.equals("/api/v1/auth/password/change"))) {
			return true;
		}
		return false;
	}

}
