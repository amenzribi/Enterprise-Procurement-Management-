package com.epi.procurement.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.RoleGrant;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

/**
 * Absence delegation (S2-AC-06): tasks created during the period go to the delegate, marked "on behalf of". The
 * delegate must hold one of the absent user's approver roles in the same company, unless an administrator records
 * the absence with {@code adminOverride}. Self-delegation and overlapping periods are refused.
 */
@Service
class Absences {

	private final ApprovalStore store;

	private final UserDirectory users;

	private final Clock clock;

	Absences(ApprovalStore store, UserDirectory users, ObjectProvider<Clock> clock) {
		this.store = store;
		this.users = users;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	List<ApprovalViews.Absence> list(long userId) {
		return views(store.absencesOf(userId));
	}

	@Transactional(readOnly = true)
	List<ApprovalViews.Absence> listForGroup(CurrentUser admin, Long userId) {
		requireAdmin(admin);
		if (userId != null) {
			requireSameGroup(admin, userId);
			return list(userId);
		}
		return views(store.absencesOfUsers(users.userIdsOfGroup(admin.groupId())));
	}

	@Transactional
	ApprovalViews.Absence create(CurrentUser actor, long userId, AbsenceInput input, boolean asAdmin) {
		if (asAdmin) {
			requireAdmin(actor);
			requireSameGroup(actor, userId);
		}
		else if (userId != actor.id()) {
			throw ApiProblemException.forbidden();
		}
		List<FieldViolation> errors = new ArrayList<>();
		if (input.delegateUserId() == null) {
			errors.add(FieldViolation.of("delegateUserId", "required"));
		}
		if (input.startsAt() == null) {
			errors.add(FieldViolation.of("startsAt", "required"));
		}
		if (input.endsAt() == null) {
			errors.add(FieldViolation.of("endsAt", "required"));
		}
		if (input.reason() != null && input.reason().length() > 300) {
			errors.add(FieldViolation.of("reason", "too-long", "max", 300));
		}
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
		if (input.delegateUserId() == userId) {
			throw new ValidationFailedException(List.of(FieldViolation.of("delegateUserId", "delegate-not-eligible")));
		}
		if (!input.endsAt().isAfter(input.startsAt())) {
			throw new ValidationFailedException(List.of(FieldViolation.of("endsAt", "invalid-value")));
		}
		CurrentUser absent = users.loadCurrentUser(userId).orElseThrow(ApiProblemException::notFound);
		CurrentUser delegate = users.loadCurrentUser(input.delegateUserId())
			.filter(d -> d.groupId() == absent.groupId())
			.orElseThrow(() -> new ValidationFailedException(
					List.of(FieldViolation.of("delegateUserId", "delegate-not-eligible"))));
		boolean override = asAdmin && Boolean.TRUE.equals(input.adminOverride());
		if (!override && !sharesApproverRole(absent, delegate)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("delegateUserId", "delegate-not-eligible")));
		}
		if (store.overlaps(userId, input.startsAt(), input.endsAt())) {
			throw new ApiProblemException(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "absence-overlap",
					"Another absence of this user overlaps this period.");
		}
		long id = store.insertAbsence(userId, delegate.id(), input.startsAt(), input.endsAt(),
				StringUtils.hasText(input.reason()) ? input.reason().strip() : null, override, actor.id(), clock.instant());
		return views(List.of(store.absence(id).orElseThrow())).get(0);
	}

	@Transactional
	void cancel(CurrentUser actor, long absenceId, boolean asAdmin) {
		ApprovalStore.Absence absence = store.absence(absenceId).orElseThrow(ApiProblemException::notFound);
		if (asAdmin) {
			requireAdmin(actor);
			requireSameGroup(actor, absence.userId());
		}
		else if (absence.userId() != actor.id()) {
			throw ApiProblemException.notFound();
		}
		store.cancelAbsence(absenceId, clock.instant());
	}

	/**
	 * Users who hold one of the caller's approver roles in the same company (candidates for an absence delegate).
	 */
	@Transactional(readOnly = true)
	List<ApprovalViews.Delegate> candidates(CurrentUser user) {
		Map<Long, ApprovalViews.Delegate> result = new java.util.LinkedHashMap<>();
		for (RoleGrant grant : user.grants()) {
			if (grant.role().isApproverRole() && grant.companyId() != null) {
				for (UserDirectory.RoleHolder holder : users.holdersOfRole(grant.role(), grant.companyId())) {
					if (holder.userId() != user.id()) {
						result.putIfAbsent(holder.userId(), new ApprovalViews.Delegate(holder.userId(), holder.displayName()));
					}
				}
			}
		}
		return List.copyOf(result.values());
	}

	private boolean sharesApproverRole(CurrentUser absent, CurrentUser delegate) {
		for (RoleGrant grant : absent.grants()) {
			if (grant.role().isApproverRole() && grant.companyId() != null
					&& delegate.hasRoleIn(grant.role(), grant.companyId())) {
				return true;
			}
		}
		return false;
	}

	private List<ApprovalViews.Absence> views(List<ApprovalStore.Absence> absences) {
		Set<Long> ids = new HashSet<>();
		absences.forEach(a -> {
			ids.add(a.userId());
			ids.add(a.delegateUserId());
			ids.add(a.createdBy());
		});
		Map<Long, UserDirectory.UserContact> contacts = users.findContacts(ids);
		Instant now = clock.instant();
		return absences.stream()
			.map(a -> new ApprovalViews.Absence(a.id(), ref(contacts, a.userId()), ref(contacts, a.delegateUserId()),
					a.startsAt(), a.endsAt(), a.reason(), a.adminOverride(), ref(contacts, a.createdBy()), a.createdAt(),
					!a.startsAt().isAfter(now) && a.endsAt().isAfter(now)))
			.toList();
	}

	private void requireSameGroup(CurrentUser admin, long userId) {
		users.findContact(userId).filter(c -> c.groupId() == admin.groupId()).orElseThrow(ApiProblemException::notFound);
	}

	static void requireAdmin(CurrentUser user) {
		if (!user.hasRole(Role.ADMIN)) {
			throw ApiProblemException.forbidden();
		}
	}

	private static ApprovalViews.UserRef ref(Map<Long, UserDirectory.UserContact> contacts, long id) {
		UserDirectory.UserContact contact = contacts.get(id);
		return new ApprovalViews.UserRef(id, contact == null ? "" : contact.displayName());
	}

	record AbsenceInput(Long delegateUserId, Instant startsAt, Instant endsAt, String reason, Boolean adminOverride) {
	}

}
