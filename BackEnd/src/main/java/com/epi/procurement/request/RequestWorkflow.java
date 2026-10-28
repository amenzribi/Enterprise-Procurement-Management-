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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

	/** IN_APPROVAL -> APPROVED (last stage approved). The requester owns it until purchasing picks it up (S3). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void markApproved(long id, long actorUserId, Instant now) {
		move(id, RequestStatus.APPROVED, actorUserId, now, null);
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
				r.getStatus().userStatus(), r.getGroupId(), r.getRequestingCompanyId(), r.getDepartmentId(),
				r.getRequesterUserId(), r.getEnteredByUserId(), r.getCurrencyCode(), r.getTotalAmount(),
				r.getCategoryId(), r.getPurchaseType() == null ? null : r.getPurchaseType().name(),
				r.getPriority() == null ? null : r.getPriority().name(), r.getRequiredDate(), r.getSubmittedAt(),
				r.getRoundNo(), r.getCurrentOwnerUserId(), r.getOwnerSince(), r.getVersion(),
				r.getLines()
					.stream()
					.map(l -> new RequestSnapshot.LineSnapshot(l.getId(), l.getLineNo(), l.getDescription(),
							l.getQuantity(), l.getUnitCode()))
					.toList());
	}

}
