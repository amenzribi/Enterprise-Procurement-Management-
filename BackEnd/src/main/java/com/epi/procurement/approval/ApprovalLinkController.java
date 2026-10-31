package com.epi.procurement.approval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.ClientAddressResolver;
import com.epi.procurement.platform.web.Idempotency;
import com.epi.procurement.platform.web.RequestRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * One-tap e-mail approval endpoints (S2-AC-07, S2DR-07, S9-AC-01/02). The token is posted in the body (URL
 * fragment, never path/query). CSRF applies; responses are {@code no-store}. Rate limits use a proxy-aware
 * client address and a per-token fingerprint.
 */
@RestController
@RequestMapping("/api/v1/approval-links")
class ApprovalLinkController {

	private final ApprovalLinks links;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	private final ClientAddressResolver addresses;

	private final RequestRateLimiter rateLimiter;

	private final int limitPerMinute;

	private final int limitPerTokenPerMinute;

	ApprovalLinkController(ApprovalLinks links, CurrentUserProvider currentUser, Idempotency idempotency,
			ClientAddressResolver addresses, RequestRateLimiter rateLimiter,
			@Value("${app.approval.link-rate-limit-per-minute:30}") int limitPerMinute,
			@Value("${app.approval.link-rate-limit-per-token-per-minute:10}") int limitPerTokenPerMinute) {
		this.links = links;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
		this.addresses = addresses;
		this.rateLimiter = rateLimiter;
		this.limitPerMinute = limitPerMinute;
		this.limitPerTokenPerMinute = limitPerTokenPerMinute;
	}

	@PostMapping("/summary")
	ResponseEntity<ApprovalViews.LinkSummary> summary(@RequestBody TokenBody body, HttpServletRequest request) {
		throttle(request, body == null ? null : body.token());
		ApprovalViews.LinkSummary summary = links.summary(body.token(),
				ApprovalLinks.sessionUser(currentUser.current()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").body(summary);
	}

	@PostMapping("/approve")
	ResponseEntity<?> approve(@RequestBody TokenBody body, HttpServletRequest request,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		throttle(request, body == null ? null : body.token());
		String actor = "link:" + (body.token() == null ? "" : LinkTokens.hash(body.token()).substring(0, 32));
		ResponseEntity<?> result = idempotency.execute(actor, key, "approve-link", () -> ResponseEntity
			.ok(links.approve(body.token(), ApprovalLinks.sessionUser(currentUser.current()))));
		return ResponseEntity.status(result.getStatusCode())
			.headers(result.getHeaders())
			.cacheControl(CacheControl.noStore())
			.header("Referrer-Policy", "no-referrer")
			.body(result.getBody());
	}

	private void throttle(HttpServletRequest request, String token) {
		String address = addresses.resolve(request);
		rateLimiter.check("approval-link:addr:" + address, limitPerMinute);
		if (StringUtils.hasText(token)) {
			String fingerprint = LinkTokens.hash(token.strip());
			rateLimiter.check("approval-link:token:" + fingerprint.substring(0, Math.min(32, fingerprint.length())),
					limitPerTokenPerMinute);
		}
	}

	record TokenBody(String token) {

		@Override
		public String toString() {
			return "TokenBody[***]";
		}

	}

}
