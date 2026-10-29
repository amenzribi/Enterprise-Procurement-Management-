package com.epi.procurement.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/suppliers")
class SupplierController {

	private final SupplierService suppliers;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	SupplierController(SupplierService suppliers, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.suppliers = suppliers;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping
	SupplierService.Page list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String visibility, @RequestParam(required = false) Long companyId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return suppliers.list(currentUser.require(), q, visibility, companyId, page, size);
	}

	@GetMapping("/{id}")
	SupplierService.Item get(@PathVariable long id) {
		return suppliers.get(currentUser.require(), id);
	}

	@PostMapping
	ResponseEntity<?> create(@RequestBody SupplierService.Body body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "supplier-create|" + body,
				() -> ResponseEntity.status(201).body(suppliers.create(user, body)));
	}

	@PutMapping("/{id}")
	ResponseEntity<?> update(@PathVariable long id, @RequestBody SupplierService.Body body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "supplier-update|" + id + "|" + body,
				() -> ResponseEntity.ok(suppliers.update(user, id, body)));
	}

	@PostMapping("/{id}/deactivate")
	ResponseEntity<?> deactivate(@PathVariable long id,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "supplier-deactivate|" + id, () -> {
			suppliers.deactivate(user, id);
			return ResponseEntity.noContent().build();
		});
	}
}
