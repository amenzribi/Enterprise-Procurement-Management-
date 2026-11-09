package com.epi.procurement.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * The request module's API for other modules (approval, document). Visibility-checked methods take the
 * {@link CurrentUser}; "system" methods are for steps authorised elsewhere (an approval task held by the actor, a
 * background job) and must only be called after that authorisation. State changes require the caller's
 * transaction, so they commit or roll back with the caller's own changes and audit event.
 */
@Service
public class RequestWorkflow {

	private final ScopedRequests requests;

	private final RequestTransitionRepository transitions;

	private final RequestParticipantRepository participants;

	private final RequestAccessPolicy policy;

	RequestWorkflow(ScopedRequests requests, RequestTransitionRepository transitions,
			RequestParticipantRepository participants, RequestAccessPolicy policy) {
		this.requests = requests;
		this.transitions = transitions;
		this.participants = participants;
		this.policy = policy;
	}

	/**
	 * The request if the user may see it, else 404 (ADR-007).
	 */
	@Transactional(readOnly = true)
	public RequestSnapshot requireVisible(CurrentUser user, long id) {
		return snapshot(requests.getVisible(user, id));
	}

	/** Visibility then exclusive row lock (purchasing assign / mode / return). */
	@Transactional(propagation = Propagation.MANDATORY)
	public RequestSnapshot requireVisibleForUpdate(CurrentUser user, long id) {
		return snapshot(requests.getVisibleForUpdate(user, id));
	}

	/**
	 * Reloads after a purchasing mutation. Authorisation already happened; visibility may have changed (for example
	 * the buying company after a purchase-mode change).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RequestSnapshot currentLocked(long id) {
		return snapshot(requests.getForSystemUpdate(id));
	}

	/**
	 * Visible non-draft requests for dashboards and exports (Sprint 8). Visibility already applied.
	 */
	@Transactional(readOnly = true)
	public Page<RequestSnapshot> searchVisible(CurrentUser user, int page, int size) {
		int safeSize = size < 1 || size > 200 ? 50 : size;
		int safePage = Math.max(0, page);
		Specification<PurchaseRequest> notDraft = (root, q, cb) -> cb.and(cb.notEqual(root.get("status"), RequestStatus.DRAFT),
				cb.isNull(root.get("archivedAt")), cb.isNull(root.get("removedAt")));
		return requests
			.search(user, notDraft, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt", "id")))
			.map(RequestWorkflow::snapshot);
	}

	/**
	 * Buying queue (S3-AC-01): visibility already applied; extra filters are ANDed.
	 */
	@Transactional(readOnly = true)
	public Page<RequestSnapshot> purchasingQueue(CurrentUser user, PurchasingQuery query) {
		int size = query.size() < 1 || query.size() > 100 ? 20 : query.size();
		int page = Math.max(0, query.page());
		Sort sort = switch (query.sort() == null ? "submittedAt" : query.sort()) {
			case "amount" -> Sort.by(query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, "totalAmount");
			case "requiredDate" -> Sort.by(query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, "requiredDate");
			case "submittedAt" -> Sort.by(query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, "submittedAt");
			default -> Sort.by(query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, "submittedAt");
		};
		// Stable secondary key so equal timestamps stay ordered across pages.
		return requests.search(user, purchasingSpec(query), PageRequest.of(page, size, sort.and(Sort.by(Sort.Direction.DESC, "id"))))
			.map(RequestWorkflow::snapshot);
	}

	public record PurchasingQuery(String q, Long buyingCompanyId, Long categoryId, String status, String priority,
			String purchaseType, String purchaseMode, Long buyerUserId, Long requesterUserId, LocalDate requiredFrom,
			LocalDate requiredTo, Collection<Long> requestingCompanyIds, java.math.BigDecimal amountMin,
			java.math.BigDecimal amountMax, Boolean complete, String sort, boolean descending, int page, int size) {
	}

	private static <E extends Enum<E>> E parameter(Class<E> type, String value, String name) {
		try {
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException ex) {
			throw ApiProblemException.invalidParameter(name);
		}
	}

	private static Specification<PurchaseRequest> purchasingSpec(PurchasingQuery query) {
		return (root, q, cb) -> {
			java.util.ArrayList<jakarta.persistence.criteria.Predicate> all = new java.util.ArrayList<>();
			// Archived/removed leave active buying queues; GLOBAL_ADMIN uses /admin/requests filters.
			all.add(cb.isNull(root.get("archivedAt")));
			all.add(cb.isNull(root.get("removedAt")));
			if (StringUtils.hasText(query.status())) {
				all.add(cb.equal(root.get("status"), parameter(RequestStatus.class, query.status(), "status")));
			}
			else {
				all.add(root.get("status")
					.in(RequestStatus.IN_PURCHASING_REVIEW, RequestStatus.ASSIGNED_TO_BUYER,
							RequestStatus.PURCHASING_RETURNED));
			}
			if (query.buyingCompanyId() != null) {
				all.add(cb.equal(root.get("buyingCompanyId"), query.buyingCompanyId()));
			}
			if (query.requestingCompanyIds() != null) {
				if (query.requestingCompanyIds().isEmpty()) {
					all.add(cb.disjunction());
				}
				else {
					all.add(root.get("requestingCompanyId").in(query.requestingCompanyIds()));
				}
			}
			if (query.categoryId() != null) {
				all.add(cb.equal(root.get("categoryId"), query.categoryId()));
			}
			if (StringUtils.hasText(query.priority())) {
				all.add(cb.equal(root.get("priority"), parameter(Priority.class, query.priority(), "priority")));
			}
			if (StringUtils.hasText(query.purchaseType())) {
				all.add(cb.equal(root.get("purchaseType"), parameter(PurchaseType.class, query.purchaseType(), "purchaseType")));
			}
			if (StringUtils.hasText(query.purchaseMode())) {
				all.add(cb.equal(root.get("purchaseMode"),
						parameter(com.epi.procurement.organization.PurchasingMode.class, query.purchaseMode(), "purchaseMode")));
			}
			if (query.buyerUserId() != null) {
				all.add(cb.equal(root.get("assignedBuyerUserId"), query.buyerUserId()));
			}
			if (query.requesterUserId() != null) {
				all.add(cb.equal(root.get("requesterUserId"), query.requesterUserId()));
			}
			if (query.requiredFrom() != null) {
				all.add(cb.greaterThanOrEqualTo(root.get("requiredDate"), query.requiredFrom()));
			}
			if (query.requiredTo() != null) {
				all.add(cb.lessThanOrEqualTo(root.get("requiredDate"), query.requiredTo()));
			}
			if (query.amountMin() != null) {
				all.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), query.amountMin()));
			}
			if (query.amountMax() != null) {
				all.add(cb.lessThanOrEqualTo(root.get("totalAmount"), query.amountMax()));
			}
			if (query.complete() != null) {
				var hasLines = q.subquery(Long.class);
				var line = hasLines.from(PurchaseRequestLine.class);
				hasLines.select(line.get("request").get("id")).where(cb.equal(line.get("request").get("id"), root.get("id")));
				var completePred = cb.and(cb.isNotNull(root.get("title")), cb.notEqual(cb.trim(root.get("title")), ""),
						cb.isNotNull(root.get("categoryId")), cb.isNotNull(root.get("requiredDate")),
						cb.exists(hasLines));
				all.add(Boolean.TRUE.equals(query.complete()) ? completePred : cb.not(completePred));
			}
			if (StringUtils.hasText(query.q())) {
				String text = "%" + query.q().strip().toLowerCase(java.util.Locale.ROOT) + "%";
				all.add(cb.or(cb.like(cb.lower(root.get("number")), text), cb.like(cb.lower(root.get("title")), text)));
			}
			return cb.and(all.toArray(jakarta.persistence.criteria.Predicate[]::new));
		};
	}

	/**
	 * Whether the user may change the request's content (fields, lines, attachments): an author while it is a draft
	 * or returned for revision.
	 */
	@Transactional(readOnly = true)
	public boolean canEditContent(CurrentUser user, long id) {
		return requests.findVisible(user, id).map(r -> policy.canEdit(user, r)).orElse(false);
	}

	/** System read (no visibility check). */
	@Transactional(readOnly = true)
	public Optional<RequestSnapshot> find(long id) {
		return requests.findForSystem(id).map(RequestWorkflow::snapshot);
	}

	/** System read (no visibility check). */
	@Transactional(readOnly = true)
	public Map<Long, RequestSnapshot> findAll(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return requests.findAllForSystem(ids)
			.stream()
			.map(RequestWorkflow::snapshot)
			.collect(Collectors.toMap(RequestSnapshot::id, Function.identity()));
	}

	/**
	 * Locks the request row (first step of the approval lock order: request -> round -> tasks -> audit).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RequestSnapshot lockForApproval(long id) {
		return snapshot(requests.getForSystemUpdate(id));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void recordApprovalBasis(long id, BigDecimal amount, String currency, BigDecimal rate, LocalDate rateDate,
			long ruleId) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		request.recordApprovalBasis(amount, currency, rate, rateDate, ruleId);
		requests.saveAndFlush(request);
	}

	/**
	 * Makes a user a participant (approver, delegate): they can see the request from now on (S2-AC-13).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void addParticipant(long id, long userId, String reason, Instant now) {
		RequestParticipant.Key key = new RequestParticipant.Key(id, userId);
		if (!participants.existsById(key)) {
			participants.saveAndFlush(new RequestParticipant(id, userId, reason, now));
		}
	}

	/**
	 * The user who must act next (first pending approver), shown as "owner, since when".
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void assignOwner(long id, long owner, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		request.assignOwner(owner, now);
		requests.saveAndFlush(request);
	}

	/** IN_APPROVAL -> IN_PURCHASING_REVIEW (last stage approved). User status stays APPROVED (S3-AC-01). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markApproved(long id, long actorUserId, Instant now) {
		move(id, RequestStatus.IN_PURCHASING_REVIEW, actorUserId, now, null);
	}

	/** IN_APPROVAL -> REJECTED (terminal). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markRejected(long id, long actorUserId, Instant now, String reason) {
		move(id, RequestStatus.REJECTED, actorUserId, now, reason);
	}

	/** IN_APPROVAL -> REVISION_REQUESTED: the authors can edit and resubmit (S2DR-04). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markReturned(long id, long actorUserId, Instant now, String reason) {
		move(id, RequestStatus.REVISION_REQUESTED, actorUserId, now, reason);
	}

	/**
	 * Sprint 1 requests still CREATED (S2DR-01): their ids, for the one-time job that starts their circuit.
	 */
	@Transactional(readOnly = true)
	public List<Long> legacySubmittedIds() {
		return requests.idsWithStatus(RequestStatus.CREATED);
	}

	/**
	 * CREATED -> IN_APPROVAL for a Sprint 1 request (S2DR-01). Returns the round number to use.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int enterApprovalFromLegacy(long id, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		if (request.getStatus() != RequestStatus.CREATED) {
			throw ApiProblemException.conflict("invalid-state", "The request is not waiting for an approval circuit.");
		}
		int round = request.nextRound();
		request.transitionTo(RequestStatus.IN_APPROVAL, request.getRequesterUserId(), now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, RequestStatus.CREATED, RequestStatus.IN_APPROVAL,
				request.getCurrentOwnerUserId(), request.getRequesterUserId(), now, "approval-circuit-started"));
		return round;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void assignBuyer(long id, long buyerUserId, Instant now, long actorUserId) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		request.assignBuyer(buyerUserId, now);
		request.transitionTo(RequestStatus.ASSIGNED_TO_BUYER, buyerUserId, now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		addParticipant(id, buyerUserId, "BUYER", now);
		transitions.save(new RequestTransition(id, from, RequestStatus.ASSIGNED_TO_BUYER, buyerUserId, actorUserId, now,
				null));
	}

	/**
	 * Buyer accepted; status stays ASSIGNED_TO_BUYER.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void buyerAccepted(long id, long buyerUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		if (request.getStatus() != RequestStatus.ASSIGNED_TO_BUYER) {
			throw ApiProblemException.conflict("invalid-state", "The request is not assigned to a buyer.");
		}
		request.assignOwner(buyerUserId, now);
		request.touch(buyerUserId, now);
		requests.saveAndFlush(request);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void clearBuyer(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		request.assignBuyer(null, now);
		request.transitionTo(RequestStatus.IN_PURCHASING_REVIEW, request.getRequesterUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.IN_PURCHASING_REVIEW,
				request.getCurrentOwnerUserId(), actorUserId, now, "assignment-returned"));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void confirmPurchaseMode(long id, com.epi.procurement.organization.PurchasingMode mode, long buyingCompanyId,
			long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		request.confirmPurchaseMode(mode, buyingCompanyId);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void returnFromPurchasing(long id, long actorUserId, Instant now, String reason) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from != RequestStatus.IN_PURCHASING_REVIEW && from != RequestStatus.ASSIGNED_TO_BUYER) {
			throw ApiProblemException.conflict("invalid-state", "The request is not in purchasing.");
		}
		request.transitionTo(RequestStatus.PURCHASING_RETURNED, request.getRequesterUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.PURCHASING_RETURNED,
				request.getCurrentOwnerUserId(), actorUserId, now, reason));
	}

	/** Buyer opens sourcing (Sprint 4). Prerequisite: ASSIGNED_TO_BUYER with an accepted assignment. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enterSourcing(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from != RequestStatus.ASSIGNED_TO_BUYER) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready for sourcing.");
		}
		request.transitionTo(RequestStatus.SOURCING, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.SOURCING, request.getCurrentOwnerUserId(),
				actorUserId, now, "rfq-opened"));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void markQuotesReceived(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from != RequestStatus.SOURCING && from != RequestStatus.QUOTES_RECEIVED) {
			throw ApiProblemException.conflict("invalid-state", "The request is not in sourcing.");
		}
		if (from == RequestStatus.QUOTES_RECEIVED) {
			return;
		}
		request.transitionTo(RequestStatus.QUOTES_RECEIVED, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.QUOTES_RECEIVED, request.getCurrentOwnerUserId(),
				actorUserId, now, "quote-received"));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enterTechnicalValidation(long id, long actorUserId, Instant now) {
		transitionEvaluation(id, actorUserId, now, RequestStatus.QUOTES_RECEIVED, RequestStatus.TECHNICAL_VALIDATION,
				"technical-validation");
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enterCommercialValidation(long id, long actorUserId, Instant now) {
		transitionEvaluation(id, actorUserId, now, RequestStatus.TECHNICAL_VALIDATION,
				RequestStatus.COMMERCIAL_VALIDATION, "commercial-validation");
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enterAwarded(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from != RequestStatus.COMMERCIAL_VALIDATION && from != RequestStatus.MANAGEMENT_VALIDATION) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready for award.");
		}
		request.transitionTo(RequestStatus.AWARDED, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.AWARDED, request.getCurrentOwnerUserId(),
				actorUserId, now, "award-recorded"));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enterOrdered(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from != RequestStatus.AWARDED && from != RequestStatus.ORDERED) {
			throw ApiProblemException.conflict("invalid-state", "The request is not awarded.");
		}
		if (from == RequestStatus.ORDERED) {
			return;
		}
		request.transitionTo(RequestStatus.ORDERED, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.ORDERED, request.getCurrentOwnerUserId(),
				actorUserId, now, "purchase-order-issued"));
	}

	/**
	 * Advances the request along shipment milestones (Sprint 6). Never enters {@link RequestStatus#ACCEPTED}
	 * (receipt acceptance is Sprint 7). Idempotent when already at or beyond the target rank within shipment/receipt
	 * delivery.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enterShipmentStatus(long id, RequestStatus to, long actorUserId, Instant now, String reason) {
		if (to.stage() != Stage.SHIPMENT && to != RequestStatus.ORDER_CONFIRMED && to != RequestStatus.DELIVERED) {
			throw new IllegalArgumentException("Not a shipment target status: " + to);
		}
		if (to == RequestStatus.ACCEPTED || to.stage() == Stage.FINANCE || to.stage() == Stage.CLOSURE) {
			throw new IllegalArgumentException("Shipment must not close or accept the request: " + to);
		}
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from.rank() >= to.rank()) {
			return;
		}
		if (from.rank() < RequestStatus.ORDERED.rank()) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ordered.");
		}
		request.transitionTo(to, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, to, request.getCurrentOwnerUserId(), actorUserId, now,
				reason == null ? "shipment-milestone" : reason));
	}

	/**
	 * Receipt / acceptance statuses (Sprint 7). Never closes the request (closure is explicit).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void enterReceiptStatus(long id, RequestStatus to, long actorUserId, Instant now, String reason) {
		if (to != RequestStatus.PARTIALLY_RECEIVED && to != RequestStatus.NON_CONFORMING
				&& to != RequestStatus.ACCEPTED) {
			throw new IllegalArgumentException("Not a receipt target status: " + to);
		}
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from == to) {
			return;
		}
		if (from.rank() >= to.rank() && from != RequestStatus.NON_CONFORMING
				&& !(from == RequestStatus.PARTIALLY_RECEIVED && to == RequestStatus.NON_CONFORMING)) {
			if (from == to) {
				return;
			}
			if (from.rank() > to.rank() && to != RequestStatus.NON_CONFORMING) {
				return;
			}
		}
		if (from.rank() < RequestStatus.DELIVERED.rank() && to != RequestStatus.ACCEPTED) {
			throw ApiProblemException.conflict("invalid-state", "The request is not delivered.");
		}
		if (to == RequestStatus.ACCEPTED && from.rank() < RequestStatus.ORDERED.rank()) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready for acceptance.");
		}
		Long owner = request.getAssignedBuyerUserId() != null ? request.getAssignedBuyerUserId()
				: request.getRequesterUserId();
		request.transitionTo(to, owner, now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, to, request.getCurrentOwnerUserId(), actorUserId, now,
				reason == null ? "receipt" : reason));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enterFinanceStatus(long id, RequestStatus to, long actorUserId, Instant now, String reason) {
		if (to != RequestStatus.INVOICED && to != RequestStatus.PAYMENT_PENDING && to != RequestStatus.PAID) {
			throw new IllegalArgumentException("Not a finance target status: " + to);
		}
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from.rank() < RequestStatus.ACCEPTED.rank()) {
			throw ApiProblemException.conflict("invalid-state", "Accept the receipt before finance status.");
		}
		if (from.rank() >= to.rank()) {
			return;
		}
		request.transitionTo(to, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, to, request.getCurrentOwnerUserId(), actorUserId, now,
				reason == null ? "finance" : reason));
	}

	/**
	 * Purchasing's decision on the service lines (D-10): those listed become goods and follow shipment and receipt; every
	 * other service line is confirmed. Authorisation happens in the caller.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void decideLineClassification(long id, Collection<Long> toGoods, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus status = request.getStatus();
		if (status == RequestStatus.PURCHASING_RETURNED || status.rank() < RequestStatus.IN_PURCHASING_REVIEW.rank()
				|| status.rank() >= RequestStatus.AWARDED.rank()) {
			throw ApiProblemException.conflict("invalid-state", "Classification is decided after approval and before award.");
		}
		List<PurchaseRequestLine> services = request.getLines().stream().filter(l -> "SERVICE".equals(l.getLineKind())).toList();
		if (services.isEmpty()) {
			throw ApiProblemException.conflict("no-service-lines", "This request has no service lines to confirm.");
		}
		if (services.stream().allMatch(PurchaseRequestLine::isServiceConfirmed)) {
			throw ApiProblemException.conflict("already-confirmed", "These service lines are already confirmed.");
		}
		if (!services.stream().map(PurchaseRequestLine::getId).toList().containsAll(toGoods)) {
			throw new com.epi.procurement.platform.web.ValidationFailedException(
					List.of(com.epi.procurement.platform.web.FieldViolation.of("reclassifyAsGoods", "invalid-value")));
		}
		for (PurchaseRequestLine line : services) {
			if (toGoods.contains(line.getId())) {
				line.setLineKind("GOODS");
			}
			else {
				line.confirmService(actorUserId, now);
			}
		}
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
	}

	/** Advances the request version after a change held elsewhere, so another open screen sees it as stale. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void touchForAudit(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void closeRequest(long id, long actorUserId, Instant now, String reason) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from == RequestStatus.CLOSED) {
			return;
		}
		if (from.rank() < RequestStatus.ACCEPTED.rank()) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready to close.");
		}
		if (from == RequestStatus.CANCELLED) {
			throw ApiProblemException.conflict("invalid-state", "A cancelled request cannot be closed.");
		}
		request.transitionTo(RequestStatus.CLOSED, actorUserId, now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, RequestStatus.CLOSED, request.getCurrentOwnerUserId(),
				actorUserId, now, reason == null ? "closure-confirmed" : reason));
	}

	private void transitionEvaluation(long id, long actorUserId, Instant now, RequestStatus expected,
			RequestStatus to, String reason) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		RequestStatus from = request.getStatus();
		if (from != expected) {
			throw ApiProblemException.conflict("invalid-state", "The request is not ready for " + to.name() + ".");
		}
		request.transitionTo(to, request.getAssignedBuyerUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, from, to, request.getCurrentOwnerUserId(), actorUserId, now, reason));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void resubmitToPurchasing(long id, long actorUserId, Instant now) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		if (request.getStatus() != RequestStatus.PURCHASING_RETURNED) {
			throw ApiProblemException.conflict("invalid-state", "The request is not returned from purchasing.");
		}
		request.transitionTo(RequestStatus.IN_PURCHASING_REVIEW,
				request.getAssignedBuyerUserId() != null ? request.getAssignedBuyerUserId()
						: request.getRequesterUserId(),
				now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, RequestStatus.PURCHASING_RETURNED, RequestStatus.IN_PURCHASING_REVIEW,
				request.getCurrentOwnerUserId(), actorUserId, now, "purchasing-resubmitted"));
	}

	private void move(long id, RequestStatus next, long actorUserId, Instant now, String comment) {
		PurchaseRequest request = requests.getForSystemUpdate(id);
		if (request.getStatus() != RequestStatus.IN_APPROVAL) {
			throw ApiProblemException.conflict("invalid-state", "The request is not in approval.");
		}
		request.transitionTo(next, request.getRequesterUserId(), now);
		request.touch(actorUserId, now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(id, RequestStatus.IN_APPROVAL, next, request.getCurrentOwnerUserId(),
				actorUserId, now, comment));
	}

	static RequestSnapshot snapshot(PurchaseRequest r) {
		return new RequestSnapshot(r.getId(), r.getNumber(), r.getTitle(), r.getStatus().name(),
				r.getStatus().userStatus(), r.getGroupId(), r.getRequestingCompanyId(), r.getChargedCompanyId(),
				r.getBuyingCompanyId(), r.getDepartmentId(), r.getRequesterUserId(), r.getEnteredByUserId(),
				r.getCurrencyCode(), r.getTotalAmount(), r.getCategoryId(),
				r.getPurchaseType() == null ? null : r.getPurchaseType().name(),
				r.getPriority() == null ? null : r.getPriority().name(), r.getRequiredDate(), r.getSubmittedAt(),
				r.getRoundNo(), r.getCurrentOwnerUserId(), r.getOwnerSince(), r.getVersion(),
				r.getAssignedBuyerUserId(), r.getPurchaseMode() == null ? null : r.getPurchaseMode().name(),
				r.isBuyingCompanyProposed(),
				r.getLines()
					.stream()
					.map(l -> new RequestSnapshot.LineSnapshot(l.getId(), l.getLineNo(), l.getDescription(),
							l.getQuantity(), l.getUnitCode(), l.getLineKind(), l.isServiceConfirmed()))
					.toList());
	}

}
