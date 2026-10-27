package com.epi.procurement.identity;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Resolves the acting user from the authenticated session (never from request parameters or bodies). The account,
 * organisation and role grants are reloaded once per HTTP request, so deactivation or role changes take effect
 * without waiting for the session to end.
 */
@Component
public class CurrentUserProvider {

	static final String CACHE_ATTRIBUTE = CurrentUserProvider.class.getName() + ".user";

	private final UserDirectory users;

	private final boolean demoActive;

	CurrentUserProvider(UserDirectory users, Environment environment) {
		this.users = users;
		this.demoActive = environment.acceptsProfiles(Profiles.of("demo"));
	}

	/**
	 * @throws ApiProblemException 401 {@code unauthenticated} when nobody is signed in or the account is no longer
	 * usable
	 */
	public CurrentUser require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
			throw unauthenticated();
		}
		RequestAttributes request = RequestContextHolder.getRequestAttributes();
		if (request != null && request.getAttribute(CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof CurrentUser cached
				&& cached.id() == principal.userId()) {
			return cached;
		}
		CurrentUser user = users.loadCurrentUser(principal.userId())
			.filter(u -> !u.demo() || demoActive)
			.orElseThrow(CurrentUserProvider::unauthenticated);
		if (request != null) {
			request.setAttribute(CACHE_ATTRIBUTE, user, RequestAttributes.SCOPE_REQUEST);
		}
		return user;
	}

	private static ApiProblemException unauthenticated() {
		return new ApiProblemException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication is required.");
	}

}
