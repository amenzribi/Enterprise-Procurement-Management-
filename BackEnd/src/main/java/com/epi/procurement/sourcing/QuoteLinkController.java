package com.epi.procurement.sourcing;

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
	PublicSummary summary(@RequestBody PublicSummaryRequest body) {
		return sourcing.publicSummary(body == null ? null : body.token());
	}

	@PostMapping("/submit")
	PublicSummary submit(@RequestBody PublicQuoteSubmit body) {
		return sourcing.publicSubmit(body);
	}
}
