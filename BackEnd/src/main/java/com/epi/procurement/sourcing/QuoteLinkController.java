package com.epi.procurement.sourcing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.platform.web.ClientAddressResolver;
import com.epi.procurement.platform.web.RequestRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Anonymous supplier quote link (S4-AC-03, S9-AC-02). Token in the body; CSRF on POST; rate-limited with
 * proxy-aware client address and per-token fingerprint.
 */
@RestController
@RequestMapping("/api/v1/quote-links")
class QuoteLinkController {

	private final SourcingService sourcing;

	private final ClientAddressResolver addresses;

	private final RequestRateLimiter rateLimiter;

	private final int limitPerMinute;

	private final int limitPerTokenPerMinute;

	QuoteLinkController(SourcingService sourcing, ClientAddressResolver addresses, RequestRateLimiter rateLimiter,
			@Value("${app.sourcing.quote-link-rate-limit-per-minute:30}") int limitPerMinute,
			@Value("${app.sourcing.quote-link-rate-limit-per-token-per-minute:10}") int limitPerTokenPerMinute) {
		this.sourcing = sourcing;
		this.addresses = addresses;
		this.rateLimiter = rateLimiter;
		this.limitPerMinute = limitPerMinute;
		this.limitPerTokenPerMinute = limitPerTokenPerMinute;
	}

	@PostMapping("/summary")
	ResponseEntity<PublicSummary> summary(@RequestBody PublicSummaryRequest body, HttpServletRequest request) {
		throttle(request, body == null ? null : body.token());
		return withLinkHeaders(ResponseEntity.ok(sourcing.publicSummary(body == null ? null : body.token())));
	}

	@PostMapping("/submit")
	ResponseEntity<PublicSummary> submit(@RequestBody PublicQuoteSubmit body, HttpServletRequest request) {
		throttle(request, body == null ? null : body.token());
		return withLinkHeaders(ResponseEntity.ok(sourcing.publicSubmit(body)));
	}

	private void throttle(HttpServletRequest request, String token) {
		String address = addresses.resolve(request);
		rateLimiter.check("quote-link:addr:" + address, limitPerMinute);
		if (StringUtils.hasText(token) && token.strip().length() >= 20) {
			String fingerprint = SourcingService.tokenFingerprint(token.strip());
			rateLimiter.check("quote-link:token:" + fingerprint, limitPerTokenPerMinute);
		}
	}

	private static <T> ResponseEntity<T> withLinkHeaders(ResponseEntity<T> body) {
		return ResponseEntity.status(body.getStatusCode())
			.header("Cache-Control", "no-store")
			.header("Referrer-Policy", "no-referrer")
			.body(body.getBody());
	}

}
