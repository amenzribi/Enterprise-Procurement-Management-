package com.epi.procurement.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/purchasing")
class PurchasingController {

	private final PurchasingService purchasing;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	PurchasingController(PurchasingService purchasing, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.purchasing = purchasing;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/queue")
	QueuePage queue(@RequestParam(required = false) String q, @RequestParam(required = false) Long buyingCompanyId,
			@RequestParam(required = false) Long categoryId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String priority, @RequestParam(required = false) String purchaseType,
			@RequestParam(required = false) String purchaseMode, @RequestParam(required = false) Long buyerUserId,
			@RequestParam(required = false) Long requesterUserId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredTo,
			@RequestParam(required = false) String countryCode,
			@RequestParam(required = false) java.math.BigDecimal amountMin,
			@RequestParam(required = false) java.math.BigDecimal amountMax,
			@RequestParam(required = false) Boolean complete, @RequestParam(required = false) String sort,
			@RequestParam(required = false) String dir, @RequestParam(required = false, defaultValue = "0") int page,
			@RequestParam(required = false, defaultValue = "20") int size) {
		return purchasing.queue(currentUser.require(),
				new QueueFilter(q, buyingCompanyId, categoryId, status, priority, purchaseType, purchaseMode,
						buyerUserId, requesterUserId, requiredFrom, requiredTo, countryCode, amountMin, amountMax,
						complete, sort, "desc".equalsIgnoreCase(dir), page, size));
	}

	@GetMapping("/queue/filter-options")
	FilterOptions filterOptions() {
		return purchasing.filterOptions(currentUser.require());
	}

	@GetMapping("/queue/count")
	Map<String, Long> count() {
		return purchasing.count(currentUser.require());
	}

	@GetMapping("/buyers")
	List<BuyerOption> buyers(@RequestParam long companyId, @RequestParam(required = false) Long categoryId) {
		return purchasing.buyers(currentUser.require(), companyId, categoryId);
	}

	@GetMapping("/requests/{id}/assignments")
	List<Assignment> assignments(@PathVariable long id) {
		return purchasing.history(currentUser.require(), id);
	}

	@PostMapping("/requests/{id}/assign")
	ResponseEntity<?> assign(@PathVariable long id, @RequestBody ActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "assign|" + id + "|" + body, () -> ResponseEntity
			.ok(purchasing.assign(user, id, body.version(), body.buyerUserId(), body.purchaseMode())));
	}

	@PostMapping("/requests/{id}/accept")
	ResponseEntity<?> accept(@PathVariable long id, @RequestBody ActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "accept|" + id + "|" + body,
				() -> ResponseEntity.ok(purchasing.accept(user, id, body.version())));
	}

	@PostMapping("/requests/{id}/return-assignment")
	ResponseEntity<?> returnAssignment(@PathVariable long id, @RequestBody ActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "return-assignment|" + id + "|" + body,
				() -> ResponseEntity.ok(purchasing.returnAssignment(user, id, body.version(), body.reason())));
	}

	@PostMapping("/requests/{id}/purchase-mode")
	ResponseEntity<?> purchaseMode(@PathVariable long id, @RequestBody ActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "purchase-mode|" + id + "|" + body,
				() -> ResponseEntity.ok(purchasing.purchaseMode(user, id, body.version(), body.purchaseMode())));
	}

	@PostMapping("/requests/{id}/completeness-return")
	ResponseEntity<?> completenessReturn(@PathVariable long id, @RequestBody ActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "completeness-return|" + id + "|" + body,
				() -> ResponseEntity.ok(purchasing.completenessReturn(user, id, body.version(), body.reason())));
	}

	@PostMapping("/requests/{id}/question")
	ResponseEntity<?> question(@PathVariable long id, @RequestBody ActionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		String text = body.question() != null ? body.question() : body.reason();
		return idempotency.execute("user:" + user.id(), key, "purchasing-question|" + id + "|" + body,
				() -> ResponseEntity.ok(purchasing.askQuestion(user, id, body.version(), text)));
	}
}
