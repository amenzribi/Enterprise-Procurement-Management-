package com.epi.procurement.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.identity.UserSummary;
import com.epi.procurement.organization.OrganizationDirectory;

/**
 * Builds API views from entities. Must run inside a transaction (lines are loaded lazily).
 */
@Component
class RequestMapper {

	private final RequestAccessPolicy policy;

	private final RequestTransitionRepository transitions;

	private final RequestReferenceData reference;

	private final OrganizationDirectory organization;

	private final UserDirectory users;

	RequestMapper(RequestAccessPolicy policy, RequestTransitionRepository transitions, RequestReferenceData reference,
			OrganizationDirectory organization, UserDirectory users) {
		this.policy = policy;
		this.transitions = transitions;
		this.reference = reference;
		this.organization = organization;
		this.users = users;
	}

	List<RequestViews.Summary> summaries(CurrentUser user, List<PurchaseRequest> requests) {
		Set<Long> userIds = new HashSet<>();
		requests.forEach(r -> userIds.add(r.getRequesterUserId()));
		Map<Long, UserSummary> people = users.findUsers(userIds);
		Map<Long, RequestViews.Ref> companies = new HashMap<>();
		Map<String, Integer> minorUnits = minorUnits();
		return requests.stream()
			.map(r -> new RequestViews.Summary(r.getId(), r.getNumber(), r.getTitle(), r.getStatus().name(),
					r.getStatus().userStatus().name(), r.getStage().name(), money(r.getTotalAmount(), r.getCurrencyCode(), minorUnits), r.getCurrencyCode(),
					r.getRequiredDate(), r.getCreatedAt(), r.getUpdatedAt(), r.getSubmittedAt(), userRef(people, r.getRequesterUserId()),
					companies.computeIfAbsent(r.getRequestingCompanyId(), this::company), policy.actions(user, r)))
			.toList();
	}

	RequestViews.Detail detail(CurrentUser user, PurchaseRequest r) {
		List<RequestTransition> timeline = transitions.findByRequestIdOrderByOccurredAtAscIdAsc(r.getId());
		Set<Long> userIds = new HashSet<>(List.of(r.getRequesterUserId(), r.getEnteredByUserId(),
				r.getUpdatedByUserId(), r.getCurrentOwnerUserId()));
		if (r.getAssignedBuyerUserId() != null) {
			userIds.add(r.getAssignedBuyerUserId());
		}
		timeline.forEach(t -> {
			userIds.add(t.getActorUserId());
			if (t.getOwnerUserId() != null) {
				userIds.add(t.getOwnerUserId());
			}
		});
		Map<Long, UserSummary> people = users.findUsers(userIds);
		Map<String, Integer> minorUnits = minorUnits();
		Map<Long, RequestViews.Ref> companies = new HashMap<>();
		Function<Long, RequestViews.Ref> companyRef = id -> id == null ? null : companies.computeIfAbsent(id, this::company);

		RequestViews.LocalizedRef category = r.getCategoryId() == null ? null
				: reference.category(r.getGroupId(), r.getCategoryId())
					.map(c -> new RequestViews.LocalizedRef(c.id(), c.code(), c.nameEn(), c.nameFr()))
					.orElse(null);
		List<RequestViews.Line> lines = r.getLines()
			.stream()
			.map(l -> new RequestViews.Line(l.getId(), l.getLineNo(), l.getStatus().name(), l.getDescription(),
					l.getItemReference(), plain(l.getQuantity()), l.getUnitCode(), plain(l.getEstimatedUnitPrice()),
					money(l.getLineTotal(), r.getCurrencyCode(), minorUnits), l.getRequiredDate(),
					l.getDeliveryLocation()))
			.toList();
		List<RequestViews.Transition> history = timeline.stream()
			.map(t -> new RequestViews.Transition(t.getFromStatus() == null ? null : t.getFromStatus().name(),
					t.getToStatus().name(), t.getStage().name(),
					t.getOwnerUserId() == null ? null : userRef(people, t.getOwnerUserId()),
					userRef(people, t.getActorUserId()), t.getOccurredAt()))
			.toList();

		return new RequestViews.Detail(r.getId(), r.getNumber(), r.getStatus().name(),
				r.getStatus().userStatus().name(), r.getStage().name(), r.getVersion(), r.getRoundNo(), r.getOrigin().name(), r.getTitle(), category, name(r.getPriority()),
				name(r.getPurchaseType()), r.getCurrencyCode(), minorUnits.getOrDefault(r.getCurrencyCode(), 2),
				companyRef.apply(r.getRequestingCompanyId()), companyRef.apply(r.getChargedCompanyId()),
				companyRef.apply(r.getBuyingCompanyId()), r.isBuyingCompanyProposed(),
				r.getAssignedBuyerUserId() == null ? null : userRef(people, r.getAssignedBuyerUserId()),
				name(r.getPurchaseMode()),
				unit(r.getDepartmentId(), organization::findDepartment), unit(r.getSiteId(), organization::findSite),
				unit(r.getCostCenterId(), organization::findCostCenter), userRef(people, r.getRequesterUserId()),
				userRef(people, r.getEnteredByUserId()), userRef(people, r.getUpdatedByUserId()), r.getBeneficiary(),
				unit(r.getBeneficiarySiteId(), organization::findSite), r.getProject(), r.getJustification(),
				r.getUrgencyJustification(), r.getBudgetLineRef(), r.getEquipmentRef(), r.getMaintenanceOrderRef(),
				r.getRequiredDate(), r.getDeliveryLocation(), money(r.getTotalAmount(), r.getCurrencyCode(), minorUnits),
				lines, userRef(people, r.getCurrentOwnerUserId()), r.getOwnerSince(), r.getSubmittedAt(),
				r.getCreatedAt(), r.getUpdatedAt(), history, policy.actions(user, r));
	}

	private Map<String, Integer> minorUnits() {
		Map<String, Integer> units = new HashMap<>();
		reference.currencies().forEach(c -> units.put(c.code(), c.minorUnits()));
		return units;
	}

	private RequestViews.Ref company(Long id) {
		return organization.findCompany(id)
			.map(c -> new RequestViews.Ref(c.id(), c.code(), c.name()))
			.orElse(new RequestViews.Ref(id, "", ""));
	}

	private static RequestViews.Ref unit(Long id,
			Function<Long, java.util.Optional<OrganizationDirectory.UnitRef>> lookup) {
		if (id == null) {
			return null;
		}
		return lookup.apply(id).map(u -> new RequestViews.Ref(u.id(), u.code(), u.name()))
			.orElse(new RequestViews.Ref(id, "", ""));
	}

	private static RequestViews.UserRef userRef(Map<Long, UserSummary> people, Long id) {
		UserSummary user = people.get(id);
		return new RequestViews.UserRef(id, user == null ? "" : user.displayName());
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	static BigDecimal money(BigDecimal amount, String currency, Map<String, Integer> minorUnits) {
		return amount == null ? null : amount.setScale(minorUnits.getOrDefault(currency, 2), RoundingMode.HALF_UP);
	}

	private static BigDecimal plain(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().scale() < 0 ? value.setScale(0)
				: value.stripTrailingZeros();
	}

}
