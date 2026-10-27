package com.epi.procurement.identity;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.platform.web.ApiProblemException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Sign-in, sign-out and CSRF bootstrap for the SPA.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SignInService signIn;

	private final UserDirectory users;

	private final MeAssembler me;

	private final SessionAuthenticationStrategy sessionStrategy;

	private final HttpSessionSecurityContextRepository contextRepository;

	private final CookieCsrfTokenRepository csrfTokenRepository;

	private final SecuritySettings settings;

	private final AuditLog auditLog;

	private final Clock clock;

	AuthController(SignInService signIn, UserDirectory users, MeAssembler me,
			SessionAuthenticationStrategy sessionStrategy, HttpSessionSecurityContextRepository contextRepository,
			CookieCsrfTokenRepository csrfTokenRepository, SecuritySettings settings, AuditLog auditLog,
			ObjectProvider<Clock> clock) {
		this.signIn = signIn;
		this.users = users;
		this.me = me;
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
		this.settings = settings;
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Makes sure the {@code XSRF-TOKEN} cookie is set. The token is deferred, so it must be read to be written
	 * (S1DR-01). Call on start-up and after sign-in or sign-out.
	 */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(HttpServletRequest request) {
		renderCsrfToken(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	MeResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
		AuthenticatedUser principal = signIn.signIn(body.email(), body.password(), request.getRemoteAddr())
			.orElseThrow(() -> new ApiProblemException(HttpStatus.UNAUTHORIZED, "invalid-credentials",
					"The e-mail or password is incorrect."));
		CurrentUser user = users.loadCurrentUser(principal.userId())
			.orElseThrow(() -> new ApiProblemException(HttpStatus.UNAUTHORIZED, "invalid-credentials",
					"The e-mail or password is incorrect."));

		List<SimpleGrantedAuthority> authorities = CurrentUserRefresh.authorities(user);
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
		// Rotates the session id and replaces the CSRF token, as formLogin() would (S1DR-02).
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);

		HttpSession session = request.getSession(true);
		SessionTimeouts.start(session, clock.instant(), settings.session());
		renderCsrfToken(request);
		return me.assemble(user, session);
	}

	/**
	 * Ends the session on the server. Idempotent: signing out without a session also answers 204.
	 */
	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
			auditLog.record(new AuditRecord(principal.userId(), principal.email(), null, "USER_SESSION",
					Long.toString(principal.userId()), "SIGNED_OUT", null, null, null, null));
		}
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		csrfTokenRepository.saveToken(null, request, response);
		return ResponseEntity.noContent().build();
	}

	private static void renderCsrfToken(HttpServletRequest request) {
		if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
			token.getToken();
		}
	}

	record LoginRequest(String email, String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=" + email + ", password=***]";
		}

	}

}
