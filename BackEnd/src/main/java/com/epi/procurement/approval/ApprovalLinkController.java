package com.epi.procurement.approval;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.Idempotency;

import jakarta.servlet.http.HttpServletRequest;

/**
 * One-tap e-mail approval endpoints (S2-AC-07, S2DR-07). The token is posted in the body (it comes from the URL
 * fragment, never from the path or query), CSRF still applies, responses are {@code no-store} with
 * {@code Referrer-Policy: no-referrer}, and both calls are rate-limited per client address.
 */
@RestController
@RequestMapping("/api/v1/approval-links")
class ApprovalLinkController {

	private final ApprovalLinks links;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	private final Clock clock;

	private final int limitPerMinute;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	ApprovalLinkController(ApprovalLinks links, CurrentUserProvider currentUser, Idempotency idempotency,
			ObjectProvider<Clock> clock, @Value("${app.approval.link-rate-limit-per-minute:30}") int limitPerMinute) {
		this.links = links;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.limitPerMinute = limitPerMinute;
	}

	@PostMapping("/summary")
	ResponseEntity<ApprovalViews.LinkSummary> summary(@RequestBody TokenBody body, HttpServletRequest request) {
		throttle(request);
		ApprovalViews.LinkSummary summary = links.summary(body.token(),
				ApprovalLinks.sessionUser(currentUser.current()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").body(summary);
	}

	@PostMapping("/approve")
	ResponseEntity<?> approve(@RequestBody TokenBody body, HttpServletRequest request,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		throttle(request);
		String actor = "link:" + (body.token() == null ? "" : LinkTokens.hash(body.token()).substring(0, 32));
		ResponseEntity<?> result = idempotency.execute(actor, key, "approve-link", () -> ResponseEntity
			.ok(links.approve(body.token(), ApprovalLinks.sessionUser(currentUser.current()))));
		return ResponseEntity.status(result.getStatusCode())
			.headers(result.getHeaders())
			.cacheControl(CacheControl.noStore())
			.header("Referrer-Policy", "no-referrer")
			.body(result.getBody());
	}

	private void throttle(HttpServletRequest request) {
		Instant now = clock.instant();
		if (windows.size() > 10_000) {
			windows.entrySet().removeIf(e -> e.getValue().start().plus(Duration.ofMinutes(1)).isBefore(now));
		}
		Window window = windows.compute(request.getRemoteAddr(), (address, current) -> current == null
				|| current.start().plus(Duration.ofMinutes(1)).isBefore(now) ? new Window(now, 1)
						: new Window(current.start(), current.count() + 1));
		if (window.count() > limitPerMinute) {
			throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests",
					"Too many attempts. Please wait a minute.");
		}
	}

	record TokenBody(String token) {

		@Override
		public String toString() {
			// Never print the token (logs, idempotency fingerprints).
			return "TokenBody[***]";
		}

	}

	private record Window(Instant start, int count) {
	}

}
