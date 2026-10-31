package com.epi.procurement.ordering;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

@Service
class OrderingService {

	private static final String ENTITY = "PURCHASE_REQUEST";

	private final OrderingStore store;

	private final PoNumbering numbering;

	private final RequestWorkflow requests;

	private final OrganizationDirectory organization;

	private final AuditLog auditLog;

	private final Notifications notifications;

	private final Clock clock;

	OrderingService(OrderingStore store, PoNumbering numbering, RequestWorkflow requests,
			OrganizationDirectory organization, AuditLog auditLog, Notifications notifications,
			ObjectProvider<Clock> clock) {
		this.store = store;
		this.numbering = numbering;
		this.requests = requests;
		this.organization = organization;
		this.auditLog = auditLog;
		this.notifications = notifications;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	AwardView getAward(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		requireOrderingViewer(user, request);
		return store.findAwardByRequest(requestId).map(this::toAward).orElse(null);
	}

	@Transactional(readOnly = true)
	PurchaseOrderView getPo(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		requireOrderingViewer(user, request);
		return store.findPoByRequest(requestId).map(this::toPo).orElse(null);
	}

	@Transactional
	AwardView award(CurrentUser user, long requestId, AwardBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireAwardActor(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		if (!"COMMERCIAL_VALIDATION".equals(request.status()) && !"MANAGEMENT_VALIDATION".equals(request.status())) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready for award.");
		}
		if (store.findAwardByRequest(requestId).isPresent()) {
			throw ApiProblemException.conflict("award-exists", "An award already exists for this request.");
		}
		if (body == null || body.quotationId() == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("quotationId", "required")));
		}
		if (!StringUtils.hasText(body.reason()) || body.reason().strip().length() < 3) {
			throw new ValidationFailedException(List.of(FieldViolation.of("reason", "required")));
		}
		OrderingStore.QuoteSnap quote = store.findSubmittedQuote(body.quotationId())
			.orElseThrow(() -> new ValidationFailedException(List.of(FieldViolation.of("quotationId", "not-found"))));
		if (quote.requestId() != requestId) {
			throw new ValidationFailedException(List.of(FieldViolation.of("quotationId", "not-for-request")));
		}
		if (!"SUBMITTED".equals(quote.status())) {
			throw ApiProblemException.conflict("quote-not-submitted", "Only a submitted quotation can be awarded.");
		}
		if (!quote.supplierActive()) {
			throw ApiProblemException.conflict("supplier-inactive", "The supplier is no longer eligible.");
		}
		List<OrderingStore.QuoteLineSnap> lines = store.quoteLines(quote.id());
		if (lines.isEmpty() || lines.stream().anyMatch(l -> l.unitPrice() == null)) {
			throw ApiProblemException.conflict("quote-incomplete", "The quotation is missing prices.");
		}
		Instant now = clock.instant();
		BigDecimal freight = nz(quote.freight());
		BigDecimal duty = nz(quote.duty());
		BigDecimal other = nz(quote.other());
		BigDecimal merchandise = nz(quote.merchandise());
		BigDecimal landed = merchandise.add(freight).add(duty).add(other);
		long awardId = store.insertAward(request.groupId(), requestId, quote.rfqId(), quote.id(), quote.supplierId(),
				request.buyingCompanyId(), request.requestingCompanyId(), request.chargedCompanyId(), quote.currency(),
				merchandise, freight, duty, other, landed, body.reason().strip(), user.id(), now);
		for (OrderingStore.QuoteLineSnap line : lines) {
			store.insertAwardLine(awardId, line.rfqLineId(), line.requestLineId(), line.lineNo(), line.description(),
					line.quantity(), line.unit(), line.unitPrice(), line.lineAmount());
			store.upsertPriceMemory(request.groupId(), quote.supplierId(), request.categoryId(),
					normalizeDescription(line.description()), line.unitPrice(), quote.currency(), line.unit(), awardId,
					now);
		}
		requests.enterAwarded(requestId, user.id(), now);
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "AWARD_RECORDED", after.status(), body.reason().strip(),
				"{\"awardId\":" + awardId + ",\"quotationId\":" + quote.id() + "}");
		notifyAuthors(after, "AWARD_RECORDED", user);
		return toAward(store.findAward(awardId).orElseThrow());
	}

	@Transactional
	PurchaseOrderView createPo(CurrentUser user, long requestId, PoCreateBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireAwardActor(user, request);
		requireRequestVersion(body == null ? null : body.requestVersion(), request);
		if (!"AWARDED".equals(request.status())) {
			throw ApiProblemException.conflict("invalid-state", "The request must be awarded before creating a PO.");
		}
		if (store.findPoByRequest(requestId).isPresent()) {
			throw ApiProblemException.conflict("po-exists", "A purchase order already exists for this request.");
		}
		OrderingStore.AwardRow award = store.findAwardByRequest(requestId)
			.orElseThrow(() -> ApiProblemException.conflict("award-missing", "Award the request first."));
		OrganizationDirectory.CompanyRef buying = organization.findCompany(request.buyingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		Instant now = clock.instant();
		int year = LocalDate.ofInstant(now, ZoneOffset.UTC).getYear();
		String number = numbering.next(buying.id(), buying.code(), year);
		String paymentTerms = body != null && StringUtils.hasText(body.paymentTerms()) ? body.paymentTerms().strip()
				: null;
		String incoterm = body != null && StringUtils.hasText(body.incoterm()) ? body.incoterm().strip() : null;
		LocalDate requiredDate = body != null && body.requiredDate() != null ? body.requiredDate()
				: request.requiredDate();
		String delivery = body != null && StringUtils.hasText(body.deliveryAddress()) ? body.deliveryAddress().strip()
				: null;
		BigDecimal total = award.merchandise().add(award.freight()).add(award.duty()).add(award.other());
		long poId = store.insertPo(request.groupId(), award.id(), requestId, number, "DRAFT", request.buyingCompanyId(),
				request.requestingCompanyId(), request.chargedCompanyId(), award.supplierId(), award.currency(),
				award.merchandise(), award.freight(), award.duty(), award.other(), total, paymentTerms, incoterm,
				requiredDate, delivery, user.id(), now);
		for (OrderingStore.AwardLineRow line : store.awardLines(award.id())) {
			store.insertPoLine(poId, line.id(), line.requestLineId(), line.lineNo(), line.description(), line.quantity(),
					line.unit(), line.unitPrice(), line.lineAmount());
		}
		if (body != null && body.milestones() != null) {
			validateMilestones(body.milestones(), award.currency());
			store.replaceMilestones(poId, body.milestones());
		}
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "PO_CREATED", after.status(), null, "{\"poId\":" + poId + ",\"number\":\"" + number + "\"}");
		return toPo(store.findPo(poId).orElseThrow());
	}

	@Transactional
	PurchaseOrderView updateDraft(CurrentUser user, long poId, PoCreateBody body) {
		OrderingStore.PoRow po = store.findPo(poId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, po.requestId());
		requireAwardActor(user, request);
		if (!"DRAFT".equals(po.status())) {
			throw ApiProblemException.conflict("po-not-draft", "Only a draft purchase order can be edited.");
		}
		if (body == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("body", "required")));
		}
		String paymentTerms = StringUtils.hasText(body.paymentTerms()) ? body.paymentTerms().strip() : null;
		String incoterm = StringUtils.hasText(body.incoterm()) ? body.incoterm().strip() : null;
		String delivery = StringUtils.hasText(body.deliveryAddress()) ? body.deliveryAddress().strip() : null;
		if (store.updateDraftPo(poId, paymentTerms, incoterm, body.requiredDate(), delivery) != 1) {
			throw ApiProblemException.conflict("po-not-draft", "Only a draft purchase order can be edited.");
		}
		if (body.milestones() != null) {
			validateMilestones(body.milestones(), po.currency());
			if (!store.replaceMilestones(poId, body.milestones())) {
				throw ApiProblemException.conflict("po-not-draft", "Only a draft purchase order can be edited.");
			}
		}
		audit(user, request, "PO_DRAFT_UPDATED", request.status(), null, "{\"poId\":" + poId + "}");
		return toPo(store.findPo(poId).orElseThrow());
	}

	@Transactional
	PurchaseOrderView issue(CurrentUser user, long poId, PoActionBody body) {
		OrderingStore.PoRow po = store.findPo(poId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, po.requestId());
		requireAwardActor(user, request);
		if (body != null && body.version() != null && body.version() != po.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The purchase order was changed by someone else. Reload it and try again.");
		}
		if (!"DRAFT".equals(po.status())) {
			if ("ISSUED".equals(po.status()) || "CONFIRMED".equals(po.status())) {
				return toPo(po);
			}
			throw ApiProblemException.conflict("po-not-draft", "Only a draft purchase order can be issued.");
		}
		Instant now = clock.instant();
		if (store.issuePo(poId, user.id(), now) == 0) {
			OrderingStore.PoRow again = store.findPo(poId).orElseThrow(ApiProblemException::notFound);
			if ("ISSUED".equals(again.status()) || "CONFIRMED".equals(again.status())) {
				return toPo(again);
			}
			throw ApiProblemException.conflict("po-not-draft", "Only a draft purchase order can be issued.");
		}
		requests.enterOrdered(po.requestId(), user.id(), now);
		RequestSnapshot after = requests.currentLocked(po.requestId());
		audit(user, after, "PO_ISSUED", after.status(), null,
				"{\"poId\":" + poId + ",\"number\":\"" + Objects.toString(po.number(), "") + "\"}");
		notifyAuthors(after, "PO_ISSUED", user);
		return toPo(store.findPo(poId).orElseThrow());
	}

	@Transactional
	PurchaseOrderView confirm(CurrentUser user, long poId, PoActionBody body) {
		OrderingStore.PoRow po = store.findPo(poId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisibleForUpdate(user, po.requestId());
		requireAwardActor(user, request);
		if (body != null && body.version() != null && body.version() != po.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The purchase order was changed by someone else. Reload it and try again.");
		}
		if ("CONFIRMED".equals(po.status())) {
			return toPo(po);
		}
		if (!"ISSUED".equals(po.status())) {
			throw ApiProblemException.conflict("po-not-issued", "Issue the purchase order before confirming it.");
		}
		Instant now = clock.instant();
		String note = body != null && StringUtils.hasText(body.confirmationNote()) ? body.confirmationNote().strip()
				: null;
		store.confirmPo(poId, note, user.id(), now);
		RequestSnapshot after = requests.currentLocked(po.requestId());
		audit(user, after, "PO_CONFIRMED", after.status(), note, "{\"poId\":" + poId + "}");
		notifyAuthors(after, "PO_CONFIRMED", user);
		return toPo(store.findPo(poId).orElseThrow());
	}

	private AwardView toAward(OrderingStore.AwardRow award) {
		List<AwardLineView> lines = store.awardLines(award.id())
			.stream()
			.map(l -> new AwardLineView(l.id(), l.lineNo(), l.description(), l.quantity(), l.unit(), l.unitPrice(),
					l.lineAmount()))
			.toList();
		return new AwardView(award.id(), award.requestId(), award.quotationId(), award.supplierId(),
				store.supplierName(award.supplierId()), award.currency(), award.merchandise(), award.freight(),
				award.duty(), award.other(), award.landed(), award.reason(), award.awardedAt(), lines);
	}

	private PurchaseOrderView toPo(OrderingStore.PoRow po) {
		List<PoLineView> lines = store.poLines(po.id())
			.stream()
			.map(l -> new PoLineView(l.id(), l.lineNo(), l.description(), l.quantity(), l.unit(), l.unitPrice(),
					l.lineAmount()))
			.toList();
		List<MilestoneView> milestones = store.milestones(po.id())
			.stream()
			.map(m -> new MilestoneView(m.id(), m.label(), m.dueDate(), m.amount(), m.currency(), m.status()))
			.toList();
		return new PurchaseOrderView(po.id(), po.awardId(), po.requestId(), po.number(), po.status(), po.supplierId(),
				store.supplierName(po.supplierId()), po.currency(), po.merchandise(), po.freight(), po.duty(), po.other(),
				po.total(), po.paymentTerms(), po.incoterm(), po.requiredDate(), po.deliveryAddress(), po.createdAt(),
				po.issuedAt(), po.confirmedAt(), po.confirmationNote(), po.version(), lines, milestones);
	}

	private void requireAwardActor(CurrentUser user, RequestSnapshot request) {
		boolean manager = user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId());
		boolean acceptedBuyer = request.assignedBuyerUserId() != null && request.assignedBuyerUserId() == user.id()
				&& store.assignmentAccepted(request.id(), user.id());
		if (!manager && !acceptedBuyer) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requireOrderingViewer(CurrentUser user, RequestSnapshot request) {
		if (!user.hasRoleIn(Role.BUYER, request.buyingCompanyId())
				&& !user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())
				&& !request.isAuthor(user.id())) {
			throw ApiProblemException.forbidden();
		}
		if (user.hasRoleIn(Role.BUYER, request.buyingCompanyId())
				&& !user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())
				&& (request.assignedBuyerUserId() == null || request.assignedBuyerUserId() != user.id())
				&& !request.isAuthor(user.id())) {
			throw ApiProblemException.forbidden();
		}
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

	private static void validateMilestones(List<MilestoneBody> milestones, String currency) {
		for (MilestoneBody m : milestones) {
			if (m == null || !StringUtils.hasText(m.label()) || m.amount() == null || m.amount().signum() < 0) {
				throw new ValidationFailedException(List.of(FieldViolation.of("milestones", "invalid")));
			}
			String ccy = StringUtils.hasText(m.currency()) ? m.currency().strip().toUpperCase(Locale.ROOT) : currency;
			if (!currency.equals(ccy)) {
				throw new ValidationFailedException(List.of(FieldViolation.of("milestones", "currency-mismatch")));
			}
			String status = m.status() == null ? "PLANNED" : m.status().strip().toUpperCase(Locale.ROOT);
			if (!"PLANNED".equals(status) && !"DUE".equals(status) && !"PAID".equals(status)) {
				throw new ValidationFailedException(List.of(FieldViolation.of("milestones", "invalid-status")));
			}
		}
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

	private static BigDecimal nz(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static String normalizeDescription(String description) {
		String norm = description == null ? "" : description.strip().toLowerCase(Locale.ROOT);
		return norm.length() <= 200 ? norm : norm.substring(0, 200);
	}

}
