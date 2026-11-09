package com.epi.procurement.receipt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.notification.Notice;
import com.epi.procurement.notification.Notifications;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestStatus;
import com.epi.procurement.request.RequestWorkflow;

@Service
class ReceiptService {

	private static final String ENTITY = "PURCHASE_REQUEST";

	private static final Set<String> CONDITIONS = Set.of("OK", "DAMAGED", "MISSING", "NON_CONFORMING");

	private static final Set<String> EVIDENCE_KINDS = Set.of("PHOTO", "DAMAGE_REPORT", "DELIVERY_NOTE", "OTHER");

	/** Exclusive upper bound of a DECIMAL(19,4) column. */
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000000");

	private final ReceiptStore store;

	private final ReceiptNumbering numbering;

	private final RequestWorkflow requests;

	private final OrganizationDirectory organization;

	private final ExchangeRates rates;

	private final AuditLog auditLog;

	private final Notifications notifications;

	private final Clock clock;

	ReceiptService(ReceiptStore store, ReceiptNumbering numbering, RequestWorkflow requests,
			OrganizationDirectory organization, ExchangeRates rates, AuditLog auditLog, Notifications notifications,
			ObjectProvider<Clock> clock) {
		this.store = store;
		this.numbering = numbering;
		this.requests = requests;
		this.organization = organization;
		this.rates = rates;
		this.auditLog = auditLog;
		this.notifications = notifications;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	ReceiptDossierView dossier(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		requireViewer(user, request);
		return buildDossier(request);
	}

	@Transactional
	ReceiptView createReceipt(CurrentUser user, long requestId, CreateReceiptBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireReceiver(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		requireDeliverable(request);
		ReceiptStore.PoSnap po = store.findPo(requestId).orElseThrow(ApiProblemException::notFound);
		if (!"CONFIRMED".equals(po.status())) {
			throw ApiProblemException.conflict("po-not-confirmed", "Confirm the purchase order before receiving.");
		}
		Long shipmentId = body == null ? null : body.shipmentId();
		if (shipmentId != null && !store.shipmentExists(shipmentId, requestId)) {
			throw ApiProblemException.notFound();
		}
		Map<Long, ReceiptStore.PoLineSnap> byId = new LinkedHashMap<>();
		for (ReceiptStore.PoLineSnap line : store.poLines(po.id())) {
			byId.put(line.id(), line);
		}
		List<ReceiptLineBody> lines = body == null || body.lines() == null || body.lines().isEmpty()
				? byId.values()
					.stream()
					.map(l -> new ReceiptLineBody(l.id(), null, l.quantity(), l.quantity(), BigDecimal.ZERO,
							BigDecimal.ZERO, "OK", null))
					.toList()
				: body.lines();
		if (lines.isEmpty()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("lines", "required")));
		}
		Instant now = clock.instant();
		OrganizationDirectory.CompanyRef requesting = organization.findCompany(request.requestingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		String number = numbering.next(requesting.id(), requesting.code(),
				LocalDate.ofInstant(now, ZoneOffset.UTC).getYear());
		long receiptId = store.insertReceipt(request.groupId(), requestId, po.id(), shipmentId, number, "RECORDED", now,
				user.id(), trim(body == null ? null : body.note()));
		short lineNo = 1;
		boolean anyBad = false;
		boolean partial = false;
		for (ReceiptLineBody lineBody : lines) {
			ReceiptStore.PoLineSnap poLine = byId.get(lineBody.purchaseOrderLineId());
			if (poLine == null) {
				throw new ValidationFailedException(List.of(FieldViolation.of("lines", "unknown-po-line")));
			}
			BigDecimal received = nz(lineBody.quantityReceived());
			BigDecimal accepted = nz(lineBody.quantityAccepted());
			BigDecimal rejected = nz(lineBody.quantityRejected());
			BigDecimal damaged = nz(lineBody.quantityDamaged());
			if (received.signum() <= 0 || !validQuantity(received) || !validQuantity(accepted)
					|| !validQuantity(rejected) || !validQuantity(damaged)) {
				throw new ValidationFailedException(List.of(FieldViolation.of("lines", "invalid-quantity")));
			}
			if (accepted.add(rejected).compareTo(received) > 0 || damaged.compareTo(received) > 0) {
				throw new ValidationFailedException(List.of(FieldViolation.of("lines", "invalid-quantity")));
			}
			String condition = resolveCondition(lineBody.condition());
			if (!"OK".equals(condition)) {
				anyBad = true;
			}
			BigDecimal cap = store.shippedQuantity(poLine.id());
			if (cap.signum() <= 0) {
				cap = poLine.quantity();
			}
			BigDecimal already = store.receivedQuantity(poLine.id());
			if (already.add(received).compareTo(cap) > 0) {
				throw ApiProblemException.conflict("quantity-exceeded",
						"Received quantity would exceed shipped (or ordered) quantity.");
			}
			if (accepted.compareTo(poLine.quantity()) < 0) {
				partial = true;
			}
			store.insertLine(receiptId, poLine.id(), lineBody.shipmentLineId(), poLine.requestLineId(), lineNo++,
					poLine.description(), received, accepted, rejected, damaged, condition, trim(lineBody.note()));
		}
		if (body != null && body.evidence() != null) {
			for (EvidenceBody evidence : body.evidence()) {
				if (evidence == null || !StringUtils.hasText(evidence.kind())) {
					continue;
				}
				String kind = evidence.kind().strip().toUpperCase(Locale.ROOT);
				if (!EVIDENCE_KINDS.contains(kind)) {
					throw new ValidationFailedException(List.of(FieldViolation.of("evidence", "invalid")));
				}
				store.insertEvidence(receiptId, kind, trim(evidence.referenceNumber()), trim(evidence.note()), user.id(),
						now);
			}
		}
		RequestStatus next = anyBad ? RequestStatus.NON_CONFORMING
				: partial ? RequestStatus.PARTIALLY_RECEIVED : RequestStatus.PARTIALLY_RECEIVED;
		requests.enterReceiptStatus(requestId, next, user.id(), now, "goods-receipt-recorded");
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "RECEIPT_RECORDED", after.status(), null,
				"{\"receiptId\":" + receiptId + ",\"number\":\"" + number + "\"}");
		notifyAuthors(after, "RECEIPT_RECORDED", user);
		return toReceiptView(store.findReceipt(receiptId).orElseThrow());
	}

	@Transactional
	ReceiptView confirmReceipt(CurrentUser user, long receiptId, ConfirmReceiptBody body) {
		ReceiptStore.ReceiptRow receipt = store.findReceipt(receiptId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, receipt.requestId());
		requireConfirmer(user, request);
		if (receipt.receivedByUserId() == user.id()) {
			// D-03: recording and accepting goods are two people's work.
			throw new ApiProblemException(HttpStatus.FORBIDDEN, "self-confirmation",
					"Another person must confirm a receipt you recorded.");
		}
		if (!"RECORDED".equals(receipt.status())) {
			throw ApiProblemException.conflict("invalid-state", "This receipt is already confirmed.");
		}
		if (body != null && body.version() != null && body.version() != receipt.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The receipt was changed by someone else. Reload it and try again.");
		}
		Instant now = clock.instant();
		// The status above was read before the request lock: a concurrent confirmation shows up here.
		if (store.confirmReceipt(receiptId, user.id(), now, trim(body == null ? null : body.note())) == 0) {
			throw ApiProblemException.conflict("invalid-state", "This receipt is already confirmed.");
		}
		ReceiptStore.PoSnap po = store.findPo(request.id()).orElseThrow(ApiProblemException::notFound);
		boolean full = true;
		boolean anyBad = false;
		for (ReceiptStore.PoLineSnap poLine : store.poLines(po.id())) {
			BigDecimal accepted = store.acceptedQuantity(poLine.id());
			// include this receipt's accepted (now confirmed)
			accepted = store.acceptedQuantity(poLine.id());
			if (accepted.compareTo(poLine.quantity()) < 0) {
				full = false;
			}
		}
		for (ReceiptStore.LineRow line : store.lines(receiptId)) {
			if (!"OK".equals(line.condition()) || line.quantityRejected().signum() > 0
					|| line.quantityDamaged().signum() > 0) {
				anyBad = true;
			}
		}
		RequestStatus next = full && !anyBad ? RequestStatus.ACCEPTED
				: anyBad ? RequestStatus.NON_CONFORMING : RequestStatus.PARTIALLY_RECEIVED;
		requests.enterReceiptStatus(request.id(), next, user.id(), now, "goods-receipt-confirmed");
		RequestSnapshot after = requests.currentLocked(request.id());
		audit(user, after, "RECEIPT_CONFIRMED", after.status(), null, "{\"receiptId\":" + receiptId + "}");
		notifyAuthors(after, "RECEIPT_CONFIRMED", user);
		return toReceiptView(store.findReceipt(receiptId).orElseThrow());
	}

	@Transactional
	ReceiptDossierView acceptService(CurrentUser user, long requestId, ServiceAcceptanceBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireConfirmer(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		if (store.hasServiceAcceptance(requestId)) {
			throw ApiProblemException.conflict("already-accepted", "Service acceptance is already recorded.");
		}
		if (!store.receiptsForRequest(requestId).isEmpty()) {
			throw ApiProblemException.conflict("invalid-state", "Goods receipts already exist; confirm those instead.");
		}
		ReceiptStore.PoSnap po = store.findPo(requestId).orElseThrow(ApiProblemException::notFound);
		if (!"CONFIRMED".equals(po.status())) {
			throw ApiProblemException.conflict("po-not-confirmed", "Confirm the purchase order first.");
		}
		if (!StringUtils.hasText(body == null ? null : body.comment()) || body.comment().strip().length() < 3) {
			throw new ValidationFailedException(List.of(FieldViolation.of("comment", "required")));
		}
		Instant now = clock.instant();
		boolean ok = body.performanceOk() == null || body.performanceOk();
		store.insertServiceAcceptance(requestId, user.id(), now, ok, body.comment().strip());
		requests.enterReceiptStatus(requestId, RequestStatus.ACCEPTED, user.id(), now, "service-acceptance");
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "SERVICE_ACCEPTED", after.status(), body.comment().strip(), null);
		notifyAuthors(after, "SERVICE_ACCEPTED", user);
		return buildDossier(after);
	}

	/**
	 * A purchasing manager accepts what was delivered despite a rejected or missing quantity (D-01). Every receipt must
	 * be confirmed first, so the decision is taken on the final counts. The request then follows the normal invoice and
	 * closure path.
	 */
	@Transactional
	ReceiptDossierView acceptShortfall(CurrentUser user, long requestId, ShortfallBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		if (!user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())) {
			throw ApiProblemException.forbidden();
		}
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		RequestStatus status = RequestStatus.valueOf(request.status());
		if (status != RequestStatus.NON_CONFORMING && status != RequestStatus.PARTIALLY_RECEIVED) {
			throw ApiProblemException.conflict("invalid-state", "Only a partial or non-conforming receipt can be accepted.");
		}
		List<ReceiptStore.ReceiptRow> receipts = store.receiptsForRequest(requestId);
		if (receipts.isEmpty() || receipts.stream().anyMatch(r -> "RECORDED".equals(r.status()))) {
			throw ApiProblemException.conflict("unconfirmed-receipts", "Confirm every receipt before accepting a shortfall.");
		}
		if (!StringUtils.hasText(body.reason()) || body.reason().strip().length() < 3) {
			throw new ValidationFailedException(List.of(FieldViolation.of("reason", "required")));
		}
		Instant now = clock.instant();
		String reason = body.reason().strip();
		store.insertShortfallAcceptance(requestId, user.id(), now, reason);
		requests.enterReceiptStatus(requestId, RequestStatus.ACCEPTED, user.id(), now, "shortfall-accepted");
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "SHORTFALL_ACCEPTED", after.status(), reason, null);
		notifyAuthors(after, "SHORTFALL_ACCEPTED", user);
		return buildDossier(after);
	}

	/** Attests the invoice match after the invoice was recorded without it; closure requires the attestation. */
	@Transactional
	ReceiptDossierView attestInvoiceMatch(CurrentUser user, long requestId, InvoiceAttestBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireBuyer(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		ReceiptStore.InvoiceRow invoice = store.findInvoice(requestId)
			.orElseThrow(() -> ApiProblemException.conflict("not-invoiced", "Record the invoice first."));
		if (invoice.matchAttested()) {
			throw ApiProblemException.conflict("already-attested", "This invoice match is already attested.");
		}
		store.attestInvoiceMatch(invoice.id());
		requests.touchForAudit(requestId, user.id(), clock.instant());
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "INVOICE_MATCH_ATTESTED", after.status(), null, "{\"invoiceId\":" + invoice.id() + "}");
		return buildDossier(after);
	}

	/** Records payment of a pending invoice (D-02). Closure does not depend on it, so it may follow closure. */
	@Transactional
	ReceiptDossierView markInvoicePaid(CurrentUser user, long requestId, InvoicePaidBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireBuyer(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		if ("CANCELLED".equals(request.status())) {
			throw ApiProblemException.conflict("invalid-state", "A cancelled request cannot be paid.");
		}
		ReceiptStore.InvoiceRow invoice = store.findInvoice(requestId)
			.orElseThrow(() -> ApiProblemException.conflict("not-invoiced", "Record the invoice first."));
		if ("PAID".equals(invoice.paymentStatus())) {
			throw ApiProblemException.conflict("already-paid", "This invoice is already marked as paid.");
		}
		Instant now = clock.instant();
		store.updateInvoicePayment(invoice.id(), "PAID");
		requests.enterFinanceStatus(requestId, RequestStatus.PAID, user.id(), now, "invoice-paid");
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "INVOICE_PAID", after.status(), null, "{\"invoiceId\":" + invoice.id() + "}");
		notifyAuthors(after, "INVOICE_PAID", user);
		return buildDossier(after);
	}

	@Transactional
	InvoiceView recordInvoice(CurrentUser user, long requestId, InvoiceBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireBuyer(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		if (RequestStatus.valueOf(request.status()).rank() < RequestStatus.ACCEPTED.rank()) {
			throw ApiProblemException.conflict("invalid-state", "Accept the receipt before recording the invoice.");
		}
		if (store.findInvoice(requestId).isPresent()) {
			throw ApiProblemException.conflict("already-invoiced", "A final invoice is already recorded.");
		}
		if (body == null || !StringUtils.hasText(body.invoiceNumber()) || body.amount() == null
				|| body.amount().signum() <= 0 || !StringUtils.hasText(body.currency())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("invoiceNumber", "required")));
		}
		ReceiptStore.PoSnap po = store.findPo(requestId).orElseThrow(ApiProblemException::notFound);
		String payment = body.paymentStatus() == null ? "PENDING" : body.paymentStatus().strip().toUpperCase(Locale.ROOT);
		if (!"PENDING".equals(payment) && !"PAID".equals(payment)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("paymentStatus", "invalid")));
		}
		String currency = body.currency().strip().toUpperCase(Locale.ROOT);
		if (!rates.currencyExists(currency)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("currency", "invalid")));
		}
		BigDecimal amount = body.amount().setScale(rates.minorUnits(currency), RoundingMode.HALF_UP);
		if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) >= 0) {
			throw new ValidationFailedException(List.of(FieldViolation.of("amount", "invalid")));
		}
		Instant now = clock.instant();
		long id = store.insertInvoice(requestId, po.id(), body.invoiceNumber().strip(), amount, currency,
				trim(body.documentReference()),
				Boolean.TRUE.equals(body.matchAttested()), payment, user.id(), now);
		RequestStatus finance = "PAID".equals(payment) ? RequestStatus.PAID : RequestStatus.INVOICED;
		requests.enterFinanceStatus(requestId, finance, user.id(), now, "supplier-invoice");
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "INVOICE_RECORDED", after.status(), null, "{\"invoiceId\":" + id + "}");
		notifyAuthors(after, "INVOICE_RECORDED", user);
		return toInvoice(store.findInvoice(requestId).orElseThrow());
	}

	@Transactional
	ReceiptDossierView close(CurrentUser user, long requestId, CloseBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireCloser(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		// Only a purchasing manager may waive an outstanding quantity (sprint-7 "Unresolved").
		String waiver = user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())
				? trim(body == null ? null : body.waiverNote())
				: null;
		ClosureView closure = evaluateClosure(request, waiver);
		if (!closure.ready()) {
			throw ApiProblemException.conflict("closure-not-ready",
					"Closure conditions are not met: " + String.join(", ", closure.missing()));
		}
		Instant now = clock.instant();
		ReceiptStore.PoSnap po = store.findPo(requestId).orElseThrow(ApiProblemException::notFound);
		if (body != null && body.rating() != null) {
			if (body.rating() < 1 || body.rating() > 5) {
				throw new ValidationFailedException(List.of(FieldViolation.of("rating", "invalid")));
			}
			if (store.findScore(requestId).isEmpty()) {
				store.insertScore(request.groupId(), requestId, po.supplierId(), body.rating(),
						trim(body.scoreComment()), user.id(), now);
			}
		}
		requests.closeRequest(requestId, user.id(), now, "closure-confirmed");
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "REQUEST_CLOSED", after.status(), waiver, null);
		notifyAuthors(after, "REQUEST_CLOSED", user);
		return buildDossier(after);
	}

	private ReceiptDossierView buildDossier(RequestSnapshot request) {
		List<ReceiptView> receipts = store.receiptsForRequest(request.id()).stream().map(this::toReceiptView).toList();
		InvoiceView invoice = store.findInvoice(request.id()).map(this::toInvoice).orElse(null);
		Integer rating = store.findScore(request.id()).map(ReceiptStore.ScoreRow::rating).orElse(null);
		boolean closed = "CLOSED".equals(request.status());
		ClosureView closure = closed ? new ClosureView(true, List.of(), true, rating)
				: evaluateClosure(request, null);
		return new ReceiptDossierView(receipts, invoice, closure, store.hasServiceAcceptance(request.id()),
				store.hasShortfallAcceptance(request.id()), purchaseOrderSummary(request.id()));
	}

	private PurchaseOrderSummary purchaseOrderSummary(long requestId) {
		return store.findPo(requestId)
			.map(po -> new PurchaseOrderSummary(po.currency(), po.totalAmount(),
					store.poLines(po.id())
						.stream()
						.map(l -> {
							BigDecimal received = store.receivedQuantity(l.id());
							BigDecimal cap = store.shippedQuantity(l.id());
							BigDecimal limit = cap.signum() > 0 ? cap : l.quantity();
							return new PurchaseOrderLineSummary(l.id(), l.description(), l.quantity(), l.unit(), received,
									limit.subtract(received).max(BigDecimal.ZERO));
						})
						.toList()))
			.orElse(null);
	}

	private ClosureView evaluateClosure(RequestSnapshot request, String waiverNote) {
		List<String> missing = new ArrayList<>();
		RequestStatus status = RequestStatus.valueOf(request.status());
		if (status.rank() < RequestStatus.ACCEPTED.rank()) {
			missing.add("acceptance");
		}
		if (store.findInvoice(request.id()).isEmpty()) {
			missing.add("invoice");
		}
		else if (!store.findInvoice(request.id()).orElseThrow().matchAttested()) {
			missing.add("match-attested");
		}
		if (!store.hasServiceAcceptance(request.id()) && !store.hasShortfallAcceptance(request.id())) {
			ReceiptStore.PoSnap po = store.findPo(request.id()).orElse(null);
			if (po != null) {
				for (ReceiptStore.PoLineSnap line : store.poLines(po.id())) {
					if (store.acceptedQuantity(line.id()).compareTo(line.quantity()) < 0
							&& !StringUtils.hasText(waiverNote)) {
						missing.add("outstanding-quantity");
						break;
					}
				}
			}
		}
		boolean ready = missing.isEmpty() || (missing.equals(List.of("outstanding-quantity"))
				&& StringUtils.hasText(waiverNote));
		if (StringUtils.hasText(waiverNote) && missing.contains("outstanding-quantity")) {
			missing = missing.stream().filter(m -> !"outstanding-quantity".equals(m)).toList();
			ready = missing.isEmpty();
		}
		Integer rating = store.findScore(request.id()).map(ReceiptStore.ScoreRow::rating).orElse(null);
		return new ClosureView(ready, List.copyOf(missing), "CLOSED".equals(request.status()), rating);
	}

	private ReceiptView toReceiptView(ReceiptStore.ReceiptRow receipt) {
		List<ReceiptLineView> lines = store.lines(receipt.id())
			.stream()
			.map(l -> new ReceiptLineView(l.id(), l.poLineId(), l.shipmentLineId(), l.requestLineId(), l.lineNo(),
					l.description(), l.quantityReceived(), l.quantityAccepted(), l.quantityRejected(),
					l.quantityDamaged(), l.condition(), l.note()))
			.toList();
		List<EvidenceView> evidence = store.evidence(receipt.id())
			.stream()
			.map(e -> new EvidenceView(e.id(), e.kind(), e.referenceNumber(), e.note(), e.fileName(), e.uploadedAt()))
			.toList();
		return new ReceiptView(receipt.id(), receipt.requestId(), receipt.purchaseOrderId(), receipt.shipmentId(),
				receipt.number(), receipt.status(), receipt.receivedAt(), receipt.confirmedAt(), receipt.note(),
				receipt.version(), receipt.receivedByUserId(), lines, evidence);
	}

	private InvoiceView toInvoice(ReceiptStore.InvoiceRow invoice) {
		return new InvoiceView(invoice.id(), invoice.requestId(), invoice.purchaseOrderId(), invoice.invoiceNumber(),
				invoice.amount(), invoice.currency(), invoice.documentReference(), invoice.matchAttested(),
				invoice.paymentStatus(), invoice.recordedAt(), invoice.version());
	}

	private void requireDeliverable(RequestSnapshot request) {
		RequestStatus status = RequestStatus.valueOf(request.status());
		if (status != RequestStatus.DELIVERED && status != RequestStatus.PARTIALLY_RECEIVED
				&& status != RequestStatus.NON_CONFORMING) {
			throw ApiProblemException.conflict("invalid-state", "Ship and deliver before recording a receipt.");
		}
	}

	private void requireReceiver(CurrentUser user, RequestSnapshot request) {
		boolean receiver = user.hasRoleIn(Role.RECEIVER, request.requestingCompanyId())
				|| user.hasRoleIn(Role.RECEIVER, request.chargedCompanyId());
		if (!receiver && !request.isAuthor(user.id())) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requireConfirmer(CurrentUser user, RequestSnapshot request) {
		if (request.isAuthor(user.id())) {
			return;
		}
		requireReceiver(user, request);
	}

	private void requireBuyer(CurrentUser user, RequestSnapshot request) {
		boolean manager = user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId());
		boolean buyer = user.hasRoleIn(Role.BUYER, request.buyingCompanyId());
		if (!manager && !buyer) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requireCloser(CurrentUser user, RequestSnapshot request) {
		if (user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId()) || request.isAuthor(user.id())) {
			return;
		}
		throw ApiProblemException.forbidden();
	}

	private void requireViewer(CurrentUser user, RequestSnapshot request) {
		if (request.isAuthor(user.id())) {
			return;
		}
		try {
			requireReceiver(user, request);
			return;
		}
		catch (ApiProblemException ignored) {
			// fall through
		}
		requireBuyer(user, request);
	}

	private static void requireRequestVersion(Integer version, RequestSnapshot request) {
		if (version == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("requestVersion", "required")));
		}
		if (version != request.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The request was changed by someone else. Reload it and try again.");
		}
	}

	private static String resolveCondition(String condition) {
		if (!StringUtils.hasText(condition)) {
			return "OK";
		}
		String code = condition.strip().toUpperCase(Locale.ROOT);
		if (!CONDITIONS.contains(code)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("condition", "invalid")));
		}
		return code;
	}

	/** Non-negative and storable in DECIMAL(19,4) without silent rounding. */
	private static boolean validQuantity(BigDecimal value) {
		return value.signum() >= 0 && value.stripTrailingZeros().scale() <= 4 && value.compareTo(MAX_AMOUNT) < 0;
	}

	private static BigDecimal nz(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private void audit(CurrentUser user, RequestSnapshot request, String action, String toStatus, String comment,
			String payload) {
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.buyingCompanyId(), ENTITY,
				Long.toString(request.id()), action, request.status(), toStatus, comment, payload));
	}

	private void notifyAuthors(RequestSnapshot request, String kind, CurrentUser actor) {
		notifications.enqueue(new Notice(kind, request.requesterUserId(), request.id(),
				Map.of("number", Objects.toString(request.number(), ""), "title", Objects.toString(request.title(), ""),
						"actor", actor.displayName()),
				List.of(), List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
				"/requests/" + request.id()));
	}

	private static String trim(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

}
