package com.epi.procurement.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.request.RequestSnapshot;

/**
 * Who approves a stage, who may receive a delegated task, and where an absent approver's task goes.
 * <ul>
 * <li>DEPARTMENT_MANAGER: managers of the request's department in the requesting company.</li>
 * <li>BUDGET_APPROVER, MANAGEMENT_APPROVER: holders of that role in the requesting company.</li>
 * <li>NAMED_USER: the named user, if active and in the same group (S2DR-12).</li>
 * </ul>
 * The requester and the entered-by user are never approvers (no self-approval, S2DR-02). An empty stage falls back
 * to its escalation role; still empty means no eligible approver.
 */
@Component
class ApproverResolver {

	static final int MAX_DELEGATION_HOPS = 3;

	private static final List<Role> APPROVER_ROLES = List.of(Role.DEPARTMENT_MANAGER, Role.BUDGET_APPROVER,
			Role.MANAGEMENT_APPROVER);

	private final UserDirectory users;

	private final ApprovalStore store;

	ApproverResolver(UserDirectory users, ApprovalStore store) {
		this.users = users;
		this.store = store;
	}

	List<Long> resolveStage(ApprovalStore.Stage stage, RequestSnapshot request) {
		Set<Long> approvers = holders(stage.approverType(), stage.approverUserId(), request);
		removeAuthors(approvers, request);
		if (approvers.isEmpty() && stage.escalationRole() != null) {
			approvers = holders(stage.escalationRole(), null, request);
			removeAuthors(approvers, request);
		}
		return List.copyOf(approvers);
	}

	/**
	 * Holders of the escalation role (for escalation e-mails); authors excluded.
	 */
	List<Long> escalationContacts(String escalationRole, RequestSnapshot request) {
		if (escalationRole == null) {
			return List.of();
		}
		Set<Long> contacts = holders(escalationRole, null, request);
		removeAuthors(contacts, request);
		return List.copyOf(contacts);
	}

	/**
	 * Whether the user could hold a task of this stage type (used to re-check a one-tap approver, S2DR-07).
	 */
	boolean isEligible(long userId, String approverType, Long namedUserId, RequestSnapshot request) {
		if (request.isAuthor(userId)) {
			return false;
		}
		if ("NAMED_USER".equals(approverType)) {
			return Objects.equals(namedUserId, userId) || holdsApproverRoleIn(userId, request.requestingCompanyId());
		}
		return holders(approverType, namedUserId, request).contains(userId);
	}

	/**
	 * Users who may receive this task by explicit delegation: eligible for the stage type, not authors, not already
	 * holding a pending task of the stage.
	 */
	List<UserDirectory.RoleHolder> eligibleDelegates(ApprovalStore.Task task, RequestSnapshot request, Set<Long> exclude) {
		List<UserDirectory.RoleHolder> result = new ArrayList<>();
		Set<Long> seen = new HashSet<>(exclude);
		List<Role> roles = "NAMED_USER".equals(task.approverType()) ? APPROVER_ROLES
				: List.of(Role.valueOf(task.approverType()));
		for (Role role : roles) {
			for (UserDirectory.RoleHolder holder : users.holdersOfRole(role, request.requestingCompanyId())) {
				if (role == Role.DEPARTMENT_MANAGER && !"NAMED_USER".equals(task.approverType())
						&& !Objects.equals(holder.effectiveDepartmentId(), request.departmentId())) {
					continue;
				}
				if (!request.isAuthor(holder.userId()) && seen.add(holder.userId())) {
					result.add(holder);
				}
			}
		}
		return result;
	}

	boolean holdsStageRole(long userId, String approverType, long companyId) {
		if ("NAMED_USER".equals(approverType)) {
			return holdsApproverRoleIn(userId, companyId);
		}
		return users.holdersOfRole(Role.valueOf(approverType), companyId).stream().anyMatch(h -> h.userId() == userId);
	}

	boolean holdsApproverRoleIn(long userId, long companyId) {
		for (Role role : APPROVER_ROLES) {
			if (users.holdersOfRole(role, companyId).stream().anyMatch(h -> h.userId() == userId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Follows absence delegations (S2-AC-06, QA2-45): up to {@value #MAX_DELEGATION_HOPS} hops with cycle detection;
	 * each delegate must be eligible for the stage unless an administrator created the absence. Unresolved chains
	 * leave the task with the designated approver, flagged {@code ABSENCE_UNRESOLVED}.
	 */
	Routing route(long approver, String approverType, Long namedUserId, RequestSnapshot request, Instant at) {
		long current = approver;
		Set<Long> visited = new LinkedHashSet<>(List.of(approver));
		for (int hop = 0; hop <= MAX_DELEGATION_HOPS; hop++) {
			Optional<ApprovalStore.Absence> absence = store.activeAbsence(current, at);
			if (absence.isEmpty()) {
				return current == approver ? new Routing(approver, null, null)
						: new Routing(current, approver, "ABSENCE_DELEGATE");
			}
			if (hop == MAX_DELEGATION_HOPS) {
				break;
			}
			long delegate = absence.get().delegateUserId();
			// The delegate holds the same approver role in the same company (any department), unless an
			// administrator explicitly allowed the delegation.
			boolean eligible = !request.isAuthor(delegate) && (absence.get().adminOverride()
					|| holdsStageRole(delegate, approverType, request.requestingCompanyId()));
			Optional<UserDirectory.UserContact> contact = users.findContact(delegate);
			if (!visited.add(delegate) || !eligible || contact.isEmpty() || !contact.get().active()) {
				break;
			}
			current = delegate;
		}
		return new Routing(approver, null, "ABSENCE_UNRESOLVED");
	}

	private Set<Long> holders(String type, Long namedUserId, RequestSnapshot request) {
		Set<Long> result = new LinkedHashSet<>();
		if ("NAMED_USER".equals(type)) {
			if (namedUserId != null) {
				users.findContact(namedUserId)
					.filter(c -> c.active() && c.groupId() == request.groupId())
					.ifPresent(c -> result.add(c.id()));
			}
			return result;
		}
		Role role = Role.valueOf(type);
		for (UserDirectory.RoleHolder holder : users.holdersOfRole(role, request.requestingCompanyId())) {
			if (role == Role.DEPARTMENT_MANAGER && !Objects.equals(holder.effectiveDepartmentId(), request.departmentId())) {
				continue;
			}
			result.add(holder.userId());
		}
		return result;
	}

	private static void removeAuthors(Set<Long> users, RequestSnapshot request) {
		users.remove(request.requesterUserId());
		users.remove(request.enteredByUserId());
	}

	/**
	 * @param original the designated approver when the task went to a delegate, else null
	 * @param note {@code ABSENCE_DELEGATE}, {@code ABSENCE_UNRESOLVED} or null
	 */
	record Routing(long assignee, Long original, String note) {
	}

}
