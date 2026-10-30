package com.epi.procurement.sourcing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous supplier quote link (S4-AC-03). Token in the body authenticates the action (same pattern as one-tap
 * approval). Mutating actions are POST only.
 */
@RestController
@RequestMapping("/api/v1/quote-links")
class QuoteLinkController {

	private final SourcingService sourcing;

	QuoteLinkController(SourcingService sourcing) {
		this.sourcing = sourcing;
	}

	@PostMapping("/summary")
	ResponseEntity<PublicSummary> summary(@RequestBody PublicSummaryRequest body) {
		return withLinkHeaders(ResponseEntity.ok(sourcing.publicSummary(body == null ? null : body.token())));
	}

	@PostMapping("/submit")
	ResponseEntity<PublicSummary> submit(@RequestBody PublicQuoteSubmit body) {
		return withLinkHeaders(ResponseEntity.ok(sourcing.publicSubmit(body)));
	}

	private static <T> ResponseEntity<T> withLinkHeaders(ResponseEntity<T> body) {
		return ResponseEntity.status(body.getStatusCode())
			.header("Cache-Control", "no-store")
			.header("Referrer-Policy", "no-referrer")
			.body(body.getBody());
	}

}
