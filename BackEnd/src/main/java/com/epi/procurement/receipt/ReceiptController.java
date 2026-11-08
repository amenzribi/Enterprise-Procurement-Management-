package com.epi.procurement.receipt;

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

@RestController
@RequestMapping("/api/v1/receipts")
class ReceiptController {

	private final ReceiptService receipts;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	ReceiptController(ReceiptService receipts, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.receipts = receipts;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/requests/{requestId}")
	ReceiptDossierView dossier(@PathVariable long requestId) {
		return receipts.dossier(currentUser.require(), requestId);
	}

	@PostMapping("/requests/{requestId}/goods-receipts")
	ResponseEntity<?> create(@PathVariable long requestId, @RequestBody(required = false) CreateReceiptBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		CreateReceiptBody payload = body == null ? new CreateReceiptBody(null, null, null, null, null) : body;
		return idempotency.execute("user:" + user.id(), key, "goods-receipt|" + requestId + "|" + payload,
				() -> ResponseEntity.ok(receipts.createReceipt(user, requestId, payload)));
	}

	@PostMapping("/goods-receipts/{receiptId}/confirm")
	ReceiptView confirm(@PathVariable long receiptId, @RequestBody(required = false) ConfirmReceiptBody body) {
		return receipts.confirmReceipt(currentUser.require(), receiptId,
				body == null ? new ConfirmReceiptBody(null, null) : body);
	}

	@PostMapping("/requests/{requestId}/service-acceptance")
	ReceiptDossierView service(@PathVariable long requestId, @RequestBody ServiceAcceptanceBody body) {
		return receipts.acceptService(currentUser.require(), requestId, body);
	}

	@PostMapping("/requests/{requestId}/invoice")
	ResponseEntity<?> invoice(@PathVariable long requestId, @RequestBody InvoiceBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "invoice|" + requestId + "|" + body,
				() -> ResponseEntity.ok(receipts.recordInvoice(user, requestId, body)));
	}

	@PostMapping("/requests/{requestId}/shortfall-acceptance")
	ReceiptDossierView shortfall(@PathVariable long requestId, @RequestBody ShortfallBody body) {
		return receipts.acceptShortfall(currentUser.require(), requestId, body);
	}

	@PostMapping("/requests/{requestId}/invoice/paid")
	ReceiptDossierView invoicePaid(@PathVariable long requestId, @RequestBody InvoicePaidBody body) {
		return receipts.markInvoicePaid(currentUser.require(), requestId, body);
	}

	@PostMapping("/requests/{requestId}/close")
	ResponseEntity<?> close(@PathVariable long requestId, @RequestBody(required = false) CloseBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		CloseBody payload = body == null ? new CloseBody(null, null, null, null) : body;
		return idempotency.execute("user:" + user.id(), key, "close|" + requestId + "|" + payload,
				() -> ResponseEntity.ok(receipts.close(user, requestId, payload)));
	}

}
