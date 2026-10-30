package com.epi.procurement.ordering;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/ordering")
class OrderingController {

	private final OrderingService ordering;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	OrderingController(OrderingService ordering, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.ordering = ordering;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/requests/{requestId}/award")
	AwardView getAward(@PathVariable long requestId) {
		AwardView award = ordering.getAward(currentUser.require(), requestId);
		if (award == null) {
			throw ApiProblemException.notFound();
		}
		return award;
	}

	@PostMapping("/requests/{requestId}/award")
	ResponseEntity<?> award(@PathVariable long requestId, @RequestBody AwardBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "award|" + requestId + "|" + body,
				() -> ResponseEntity.ok(ordering.award(user, requestId, body)));
	}

	@GetMapping("/requests/{requestId}/purchase-order")
	PurchaseOrderView getPo(@PathVariable long requestId) {
		PurchaseOrderView po = ordering.getPo(currentUser.require(), requestId);
		if (po == null) {
			throw ApiProblemException.notFound();
		}
		return po;
	}

	@PostMapping("/requests/{requestId}/purchase-order")
	ResponseEntity<?> createPo(@PathVariable long requestId, @RequestBody(required = false) PoCreateBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		PoCreateBody payload = body == null ? new PoCreateBody(null, null, null, null, null, null) : body;
		return idempotency.execute("user:" + user.id(), key, "po-create|" + requestId + "|" + payload,
				() -> ResponseEntity.ok(ordering.createPo(user, requestId, payload)));
	}

	@PutMapping("/purchase-orders/{poId}")
	PurchaseOrderView updateDraft(@PathVariable long poId, @RequestBody PoCreateBody body) {
		return ordering.updateDraft(currentUser.require(), poId, body);
	}

	@PostMapping("/purchase-orders/{poId}/issue")
	ResponseEntity<?> issue(@PathVariable long poId, @RequestBody(required = false) PoActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		PoActionBody payload = body == null ? new PoActionBody(null, null) : body;
		return idempotency.execute("user:" + user.id(), key, "po-issue|" + poId + "|" + payload,
				() -> ResponseEntity.ok(ordering.issue(user, poId, payload)));
	}

	@PostMapping("/purchase-orders/{poId}/confirm")
	ResponseEntity<?> confirm(@PathVariable long poId, @RequestBody(required = false) PoActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		PoActionBody payload = body == null ? new PoActionBody(null, null) : body;
		return idempotency.execute("user:" + user.id(), key, "po-confirm|" + poId + "|" + payload,
				() -> ResponseEntity.ok(ordering.confirm(user, poId, payload)));
	}

}
