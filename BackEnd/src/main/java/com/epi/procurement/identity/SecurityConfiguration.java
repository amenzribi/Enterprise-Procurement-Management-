package com.epi.procurement.identity;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.epi.procurement.audit.AuditLog;

/**
 * Session-based security for the Angular SPA (sprint-1 design, S1DR-01/02/13).
 * <ul>
 * <li>Server-side HTTP session with an {@code HttpOnly}, {@code SameSite=Lax} cookie, {@code Secure} outside
 * development; session id rotated at sign-in.</li>
 * <li>CSRF with Spring Security's SPA configuration: the token is published in the readable {@code XSRF-TOKEN}
 * cookie and must be echoed in the {@code X-XSRF-TOKEN} header, which Angular's HttpClient does natively.</li>
 * <li>No form login, HTTP basic, default logout or saved requests: the API answers with JSON problem details.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecuritySettings.class)
class SecurityConfiguration {

	static final List<String> API_DOC_PATHS = List.of("/api/v1/openapi/**", "/api/v1/docs/**", "/api/v1/openapi",
			"/api/v1/docs", "/swagger-ui/**", "/v3/api-docs/**");

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			HttpSessionSecurityContextRepository securityContextRepository, SecuritySettings settings,
			ObjectProvider<Clock> clock, UserDirectory users, Environment environment) throws Exception {
		// Not a bean: a Filter bean would also be registered in the servlet container's chain.
		SessionTimeouts sessionTimeouts = new SessionTimeouts(settings.session(), clock.getIfAvailable(Clock::systemUTC));
		ApiSecurityHandlers handlers = new ApiSecurityHandlers();
		http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.requestCache(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
			.addFilterAfter(sessionTimeouts, SecurityContextHolderFilter.class)
			.addFilterAfter(new CurrentUserRefresh(users, securityContextRepository,
					environment.acceptsProfiles(Profiles.of("demo"))), SessionTimeouts.class)
			.addFilterAfter(new MustChangePasswordFilter(), CurrentUserRefresh.class)
			.addFilterAfter(new SupplierPortalRestrictionFilter(), MustChangePasswordFilter.class)
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/redeem").permitAll()
				// One-tap e-mail approval (S2-AC-07): the token in the body authenticates the single action.
				.requestMatchers(HttpMethod.POST, "/api/v1/approval-links/summary", "/api/v1/approval-links/approve")
				.permitAll()
				// Supplier quote link (S4-AC-03): token in the body authenticates the single action.
				.requestMatchers(HttpMethod.POST, "/api/v1/quote-links/summary", "/api/v1/quote-links/submit")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers(API_DOC_PATHS.toArray(String[]::new)).hasAnyRole(Role.ADMIN.name(),
						Role.GLOBAL_ADMIN.name())
				.anyRequest().authenticated());
		return http.build();
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(SecuritySettings settings) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(settings.cookieSecure()));
		return repository;
	}

	@Bean
	HttpSessionSecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * Applied explicitly by the JSON sign-in endpoint, which does not get formLogin()'s automatic protections
	 * (S1DR-02): rotate the session id (session fixation) and replace the CSRF token.
	 */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokenRepository) {
		CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokenRepository);
		csrf.setRequestHandler(new CsrfTokenRequestAttributeHandler());
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
	}

	/**
	 * Sign-in goes through {@link SignInService}; this bean only stops Spring Boot from creating its default in-memory
	 * user with a generated password logged at start-up (QA1-D-04).
	 */
	@Bean
	UserDetailsService noUserDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("Local sign-in uses POST /api/v1/auth/login");
		};
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	LoginThrottle loginThrottle(SecuritySettings settings, ObjectProvider<Clock> clock) {
		return new LoginThrottle(settings.login(), clock.getIfAvailable(Clock::systemUTC));
	}

	@Bean
	SignInService signInService(CredentialStore credentials, PasswordEncoder passwordEncoder, LoginThrottle throttle,
			AuditLog auditLog, Environment environment) {
		return new SignInService(credentials, passwordEncoder, throttle, auditLog,
				environment.acceptsProfiles(Profiles.of("demo")));
	}

}
