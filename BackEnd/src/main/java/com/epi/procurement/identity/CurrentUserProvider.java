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

	/**
	 * The signed-in user, or empty for anonymous callers (endpoints open to both, e.g. one-tap approval links).
	 */
	public java.util.Optional<CurrentUser> current() {
		try {
			return java.util.Optional.of(require());
		}
		catch (ApiProblemException ex) {
			return java.util.Optional.empty();
		}
	}

	/**
	 * Loads a user to act on their behalf without a session (one-tap approval link): active accounts only, and demo
	 * accounts only while the demo profile is active (S1DR-05).
	 */
	public java.util.Optional<CurrentUser> loadActingUser(long userId) {
		return users.loadCurrentUser(userId).filter(u -> !u.demo() || demoActive);
	}

	private static ApiProblemException unauthenticated() {
		return new ApiProblemException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication is required.");
	}

}
