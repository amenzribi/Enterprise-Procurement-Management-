package com.epi.procurement.identity;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SUPPLIER_CONTACT principals may only use the supplier portal messaging APIs and identity bootstrap endpoints
 * (ADR-011). All other authenticated APIs return 403.
 */
final class SupplierPortalRestrictionFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Object cached = request.getAttribute(CurrentUserProvider.CACHE_ATTRIBUTE);
		if (cached instanceof CurrentUser user && user.hasRole(Role.SUPPLIER_CONTACT) && !allowed(request)) {
			ProblemResponses.write(response, 403, "Forbidden", "supplier-portal-only",
					"Supplier contacts may only use the supplier messaging portal.");
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean allowed(HttpServletRequest request) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if (path.startsWith("/api/v1/supplier-portal/")) {
			return true;
		}
		if (HttpMethod.GET.matches(method) && (path.equals("/api/v1/me") || path.equals("/api/v1/auth/csrf"))) {
			return true;
		}
		if (HttpMethod.POST.matches(method) && (path.equals("/api/v1/auth/logout")
				|| path.equals("/api/v1/auth/password") || path.equals("/api/v1/auth/password/change"))) {
			return true;
		}
		if (HttpMethod.PUT.matches(method) && path.equals("/api/v1/me/preferences")) {
			return true;
		}
		return false;
	}

}
