package com.epi.procurement.request;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.RoleGrant;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Who may see and change purchase requests in Sprint 1 (docs/product/sprint-1.md, visibility table; ADR-007).
 * <ul>
 * <li>Requester and entered-by user: their requests, including drafts; they may edit, submit and discard drafts
 * while they still hold REQUESTER or ENTER_ON_BEHALF in the requesting company.</li>
 * <li>DEPARTMENT_MANAGER: non-draft requests of the granted department (or their own department) in the granted
 * company.</li>
 * <li>GROUP_MANAGEMENT: non-draft requests of every company of their group (or of the granted company).</li>
 * <li>Everyone else, including BUYER, PURCHASING_MANAGER and ADMIN: nothing yet.</li>
 * </ul>
 * Drafts are never visible to anyone but their requester and entered-by user.
 */
@Component
class RequestAccessPolicy {

	/**
	 * The visibility rule as a query predicate, so lists, counts and single lookups apply the same rule.
	 */
	Specification<PurchaseRequest> visibleTo(CurrentUser user) {
		return (root, query, cb) -> visiblePredicate(user, root, cb);
	}

	boolean canCreateIn(CurrentUser user, long companyId) {
		return user.hasRoleIn(Role.REQUESTER, companyId) || user.hasRoleIn(Role.ENTER_ON_BEHALF, companyId);
	}

	boolean canEnterOnBehalfIn(CurrentUser user, long companyId) {
		return user.hasRoleIn(Role.ENTER_ON_BEHALF, companyId);
	}

	/**
	 * Whether the user may act on the request as its author: its requester or the user who entered it, still holding
	 * REQUESTER or ENTER_ON_BEHALF in the requesting company. Other users who can see the request (managers, group
	 * management) are never editors.
	 */
	boolean isEditor(CurrentUser user, PurchaseRequest request) {
		return isAuthor(user, request) && canCreateIn(user, request.getRequestingCompanyId());
	}

	/**
	 * Edit, submit and discard: a draft, by an editor.
	 */
	boolean canEdit(CurrentUser user, PurchaseRequest request) {
		return request.getStatus() == RequestStatus.DRAFT && isEditor(user, request);
	}

	List<String> actions(CurrentUser user, PurchaseRequest request) {
		return canEdit(user, request) ? List.of("EDIT", "SUBMIT", "DISCARD") : List.of();
	}

	private static boolean isAuthor(CurrentUser user, PurchaseRequest request) {
		return request.getRequesterUserId() == user.id() || request.getEnteredByUserId() == user.id();
	}

	private static Predicate visiblePredicate(CurrentUser user, Root<PurchaseRequest> root, CriteriaBuilder cb) {
		List<Predicate> any = new ArrayList<>();
		any.add(cb.equal(root.get("requesterUserId"), user.id()));
		any.add(cb.equal(root.get("enteredByUserId"), user.id()));
		Predicate notDraft = cb.notEqual(root.get("status"), RequestStatus.DRAFT);

		for (RoleGrant grant : user.grants(Role.DEPARTMENT_MANAGER)) {
			Long department = grant.departmentId() != null ? grant.departmentId() : user.departmentId();
			Long company = grant.companyId() != null ? grant.companyId() : null;
			if (department != null && company != null) {
				any.add(cb.and(notDraft, cb.equal(root.get("requestingCompanyId"), company),
						cb.equal(root.get("departmentId"), department)));
			}
		}
		for (RoleGrant grant : user.grants(Role.GROUP_MANAGEMENT)) {
			any.add(grant.companyId() == null
					? cb.and(notDraft, cb.equal(root.get("groupId"), user.groupId()))
					: cb.and(notDraft, cb.equal(root.get("requestingCompanyId"), grant.companyId())));
		}
		return cb.or(any.toArray(Predicate[]::new));
	}

}
