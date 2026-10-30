package com.epi.procurement.sourcing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;
import com.epi.procurement.request.RequestSnapshot;

@RestController
@RequestMapping("/api/v1/sourcing")
class SourcingController {

	private final SourcingService sourcing;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	SourcingController(SourcingService sourcing, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.sourcing = sourcing;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/requests/{requestId}/rfq")
	RfqDetail get(@PathVariable long requestId) {
		RfqDetail detail = sourcing.getForRequest(currentUser.require(), requestId);
		if (detail == null) {
			throw com.epi.procurement.platform.web.ApiProblemException.notFound();
		}
		return detail;
	}

	@PostMapping("/requests/{requestId}/rfq")
	ResponseEntity<?> create(@PathVariable long requestId, @RequestBody CreateRfqBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "rfq-create|" + requestId + "|" + body,
				() -> ResponseEntity.ok(sourcing.create(user, requestId, body)));
	}

	@PostMapping("/rfqs/{rfqId}/suppliers/{rfqSupplierId}/link")
	LinkCreated link(@PathVariable long rfqId, @PathVariable long rfqSupplierId) {
		// No Idempotency store: the response contains a plaintext token that must not sit in
		// idempotency_record.response_json (S4SL-02). Double-clicks may issue a second token.
		return sourcing.issueLink(currentUser.require(), rfqId, rfqSupplierId);
	}

	@PostMapping("/rfqs/{rfqId}/quotes")
	ResponseEntity<?> quote(@PathVariable long rfqId, @RequestBody QuoteBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "rfq-quote|" + rfqId + "|" + body,
				() -> ResponseEntity.ok(sourcing.submitBuyerQuote(user, rfqId, body)));
	}

	@GetMapping("/rfqs/{rfqId}/comparison")
	ComparisonView comparison(@PathVariable long rfqId) {
		return sourcing.comparison(currentUser.require(), rfqId);
	}

	@PostMapping("/rfqs/{rfqId}/close")
	ResponseEntity<?> close(@PathVariable long rfqId,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "rfq-close|" + rfqId,
				() -> ResponseEntity.ok(sourcing.close(user, rfqId)));
	}

	@PostMapping("/requests/{requestId}/validate-technical")
	ResponseEntity<?> technical(@PathVariable long requestId, @RequestBody ValidateBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "validate-tech|" + requestId + "|" + body, () -> {
			RequestSnapshot after = sourcing.validateTechnical(user, requestId, body);
			return ResponseEntity.ok(after);
		});
	}

	@PostMapping("/requests/{requestId}/validate-commercial")
	ResponseEntity<?> commercial(@PathVariable long requestId, @RequestBody ValidateBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "validate-comm|" + requestId + "|" + body, () -> {
			RequestSnapshot after = sourcing.validateCommercial(user, requestId, body);
			return ResponseEntity.ok(after);
		});
	}
}
