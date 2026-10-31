package com.epi.procurement.identity;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Reloads the signed-in account and its roles on every request (S1CR-02). URL rules that check authorities (API
 * docs, actuator health details) therefore see a revoked role at once instead of at the end of the session, and a
 * deactivated account - or a demo account once the demo profile is off - is signed out immediately.
 * <p>
 * Sprint 10: sessions whose {@link SessionCredentials} epoch no longer matches {@code credentials_version} are
 * invalidated (password change / reset / deactivation).
 * <p>
 * The loaded user is cached for the request, so {@link CurrentUserProvider} does not query again.
 */
final class CurrentUserRefresh extends OncePerRequestFilter {

	private final UserDirectory users;

	private final SecurityContextRepository contextRepository;

	private final boolean demoActive;

	CurrentUserRefresh(UserDirectory users, SecurityContextRepository contextRepository, boolean demoActive) {
		this.users = users;
		this.contextRepository = contextRepository;
		this.demoActive = demoActive;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
			Optional<CurrentUser> user = users.loadCurrentUser(principal.userId()).filter(u -> !u.demo() || demoActive);
			HttpSession session = request.getSession(false);
			Integer sessionVersion = session == null ? null : SessionCredentials.version(session);
			boolean versionOk = user.isPresent() && sessionVersion != null
					&& sessionVersion == user.get().credentialsVersion();
			if (user.isEmpty() || !versionOk) {
				if (session != null) {
					session.invalidate();
				}
				SecurityContextHolder.clearContext();
			}
			else {
				request.setAttribute(CurrentUserProvider.CACHE_ATTRIBUTE, user.get());
				List<SimpleGrantedAuthority> authorities = authorities(user.get());
				if (!sameAuthorities(authentication, authorities)) {
					SecurityContext context = SecurityContextHolder.getContext();
					context.setAuthentication(
							UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
					contextRepository.saveContext(context, request, response);
				}
			}
		}
		chain.doFilter(request, response);
	}

	static List<SimpleGrantedAuthority> authorities(CurrentUser user) {
		return user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
	}

	private static boolean sameAuthorities(Authentication authentication, List<SimpleGrantedAuthority> authorities) {
		Set<String> current = new HashSet<>();
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			current.add(authority.getAuthority());
		}
		Set<String> fresh = new HashSet<>();
		authorities.forEach(authority -> fresh.add(authority.getAuthority()));
		return current.equals(fresh);
	}

}
