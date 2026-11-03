package com.epi.procurement.request;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * The only way to read purchase requests: every query is combined with the caller's visibility (ADR-007, DR-02).
 * A request outside the caller's visibility behaves exactly like a request that does not exist (404).
 */
@Component
class ScopedRequests {

	private final PurchaseRequestRepository repository;

	private final RequestAccessPolicy policy;

	ScopedRequests(PurchaseRequestRepository repository, RequestAccessPolicy policy) {
		this.repository = repository;
		this.policy = policy;
	}

	Page<PurchaseRequest> search(CurrentUser user, Specification<PurchaseRequest> filter, Pageable pageable) {
		return repository.findAll(policy.visibleTo(user).and(filter), pageable);
	}

	/**
	 * Installation/group-scoped search for GLOBAL_ADMIN request administration. Does not grant ordinary
	 * business visibility; callers must enforce {@code REQUEST_ADMIN} separately.
	 */
	Page<PurchaseRequest> searchInGroup(long groupId, Specification<PurchaseRequest> filter, Pageable pageable) {
		return repository.findAll(inGroup(groupId).and(filter), pageable);
	}

	PurchaseRequest getInGroup(long groupId, long id) {
		return repository.findOne(inGroup(groupId).and(hasId(id))).orElseThrow(ApiProblemException::notFound);
	}

	PurchaseRequest getInGroupForUpdate(long groupId, long id) {
		if (!repository.exists(inGroup(groupId).and(hasId(id)))) {
			throw ApiProblemException.notFound();
		}
		return repository.findByIdForUpdate(id).orElseThrow(ApiProblemException::notFound);
	}

	java.util.List<PurchaseRequest> findInGroupByIds(long groupId, java.util.Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return java.util.List.of();
		}
		Specification<PurchaseRequest> byIds = (root, query, cb) -> root.get("id").in(ids);
		return repository.findAll(inGroup(groupId).and(byIds));
	}

	PurchaseRequest getVisible(CurrentUser user, long id) {
		return repository.findOne(policy.visibleTo(user).and(hasId(id))).orElseThrow(ApiProblemException::notFound);
	}

	/**
	 * Checks visibility, then loads the request with an update lock (first step of the submit lock order).
	 */
	PurchaseRequest getVisibleForUpdate(CurrentUser user, long id) {
		if (!repository.exists(policy.visibleTo(user).and(hasId(id)))) {
			throw ApiProblemException.notFound();
		}
		return repository.findByIdForUpdate(id).orElseThrow(ApiProblemException::notFound);
	}

	Optional<PurchaseRequest> findVisible(CurrentUser user, long id) {
		return repository.findOne(policy.visibleTo(user).and(hasId(id)));
	}

	/**
	 * System access without the caller's visibility, for workflow steps authorised by other means (an approval task
	 * assigned to the actor, a background job). Never exposed to a controller directly.
	 */
	Optional<PurchaseRequest> findForSystem(long id) {
		return repository.findById(id);
	}

	PurchaseRequest getForSystemUpdate(long id) {
		return repository.findByIdForUpdate(id).orElseThrow(ApiProblemException::notFound);
	}

	java.util.List<PurchaseRequest> findAllForSystem(java.util.Collection<Long> ids) {
		return repository.findAllById(ids);
	}

	java.util.List<Long> idsWithStatus(RequestStatus status) {
		Specification<PurchaseRequest> withStatus = (root, query, cb) -> cb.equal(root.get("status"), status);
		return repository.findAll(withStatus).stream().map(PurchaseRequest::getId).toList();
	}

	PurchaseRequest saveAndFlush(PurchaseRequest request) {
		return repository.saveAndFlush(request);
	}

	void delete(PurchaseRequest request) {
		repository.delete(request);
		repository.flush();
	}

	private static Specification<PurchaseRequest> hasId(long id) {
		return (root, query, cb) -> cb.equal(root.get("id"), id);
	}

	private static Specification<PurchaseRequest> inGroup(long groupId) {
		return (root, query, cb) -> cb.equal(root.get("groupId"), groupId);
	}

}
