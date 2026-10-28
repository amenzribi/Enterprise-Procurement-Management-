package com.epi.procurement.approval;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

/**
 * Administration of approval rules and exchange rates (S2-AC-02/03, UC-28), ADMIN of the group only.
 * <ul>
 * <li>Rules are versioned: a draft never used can be edited; editing an active or used version creates a new draft
 * version of the same rule. Used versions are immutable (S2DR-05).</li>
 * <li>Activation refuses a rule whose band overlaps another active rule of the same company, category and purchase
 * type ({@code ambiguous-approval-rule}); activating a version retires the previous active version.</li>
 * <li>Exchange rates are immutable; a correction is a new row (S2DR-06).</li>
 * </ul>
 */
@Service
class ApprovalRulesAdmin {

	private static final Set<String> TYPES = Set.of("DEPARTMENT_MANAGER", "BUDGET_APPROVER", "MANAGEMENT_APPROVER",
			"NAMED_USER");

	private static final Set<String> ESCALATION_ROLES = Set.of("DEPARTMENT_MANAGER", "BUDGET_APPROVER",
			"MANAGEMENT_APPROVER");

	private final ApprovalStore store;

	private final OrganizationDirectory organization;

	private final UserDirectory users;

	private final ExchangeRates rates;

	private final Clock clock;

	private final com.epi.procurement.platform.persistence.TransactionLocks locks;

	ApprovalRulesAdmin(ApprovalStore store, OrganizationDirectory organization, UserDirectory users, ExchangeRates rates,
			ObjectProvider<Clock> clock, com.epi.procurement.platform.persistence.TransactionLocks locks) {
		this.locks = locks;
		this.store = store;
		this.organization = organization;
		this.users = users;
		this.rates = rates;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@Transactional(readOnly = true)
	List<ApprovalViews.Rule> list(CurrentUser admin, Long companyId) {
		Absences.requireAdmin(admin);
		return store.latestRules(admin.groupId(), companyId).stream().map(this::view).toList();
	}

	@Transactional(readOnly = true)
	ApprovalViews.Rule get(CurrentUser admin, long id) {
		return view(ruleOfGroup(admin, id));
	}

	@Transactional(readOnly = true)
	List<ApprovalViews.Rule> versions(CurrentUser admin, long id) {
		return store.ruleVersions(ruleOfGroup(admin, id).ruleKey()).stream().map(this::view).toList();
	}

	@Transactional
	ApprovalViews.Rule create(CurrentUser admin, RuleInput input) {
		Absences.requireAdmin(admin);
		List<ApprovalStore.Stage> stages = validate(admin, input);
		long id = store.insertRule(admin.groupId(), input.companyId(), null, 1, input.name().strip(), input.categoryId(),
				input.purchaseType(), min(input), input.maxAmount(), admin.id(), clock.instant(), stages);
		return view(store.rule(id).orElseThrow());
	}

	@Transactional
	ApprovalViews.Rule update(CurrentUser admin, long id, RuleInput input) {
		ApprovalStore.Rule current = ruleOfGroup(admin, id);
		if (input.companyId() != null && input.companyId() != current.companyId()) {
			throw new ValidationFailedException(List.of(FieldViolation.of("companyId", "not-editable")));
		}
		RuleInput effective = new RuleInput(current.companyId(), input.name(), input.categoryId(), input.purchaseType(),
				input.minAmount(), input.maxAmount(), input.stages());
		List<ApprovalStore.Stage> stages = validate(admin, effective);
		if ("DRAFT".equals(current.status()) && !store.ruleUsed(id)) {
			store.updateDraftRule(id, effective.name().strip(), effective.categoryId(), effective.purchaseType(),
					min(effective), effective.maxAmount(), stages);
			return view(store.rule(id).orElseThrow());
		}
		int nextVersion = store.ruleVersions(current.ruleKey())
			.stream()
			.mapToInt(ApprovalStore.Rule::versionNo)
			.max()
			.orElse(0) + 1;
		long newId = store.insertRule(admin.groupId(), current.companyId(), current.ruleKey(), nextVersion,
				effective.name().strip(), effective.categoryId(), effective.purchaseType(), min(effective),
				effective.maxAmount(), admin.id(), clock.instant(), stages);
		return view(store.rule(newId).orElseThrow());
	}

	@Transactional
	ApprovalViews.Rule activate(CurrentUser admin, long id) {
		ApprovalStore.Rule rule = ruleOfGroup(admin, id);
		if (!"DRAFT".equals(rule.status())) {
			throw ApiProblemException.conflict("invalid-state", "Only a draft rule version can be activated.");
		}
		// Two administrators activating overlapping rules at the same time must not both pass the check (S2CR-07).
		locks.lockExclusively("approval-rule-activation:" + rule.companyId());
		boolean overlap = store.activeRulesOfCompany(rule.companyId())
			.stream()
			.filter(other -> other.ruleKey() != rule.ruleKey())
			.anyMatch(rule::overlaps);
		if (overlap) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "ambiguous-approval-rule",
					"This rule overlaps another active rule of the same company, category and purchase type.");
		}
		store.activateRule(id, rule.ruleKey(), clock.instant());
		return view(store.rule(id).orElseThrow());
	}

	@Transactional
	ApprovalViews.Rule retire(CurrentUser admin, long id) {
		if (!"ACTIVE".equals(ruleOfGroup(admin, id).status())) {
			throw ApiProblemException.conflict("invalid-state", "Only an active rule version can be retired.");
		}
		store.retireRule(id);
		return view(store.rule(id).orElseThrow());
	}

	@Transactional(readOnly = true)
	List<ApprovalViews.Rate> rates(CurrentUser admin) {
		Absences.requireAdmin(admin);
		return rates.list(admin.groupId())
			.stream()
			.map(r -> new ApprovalViews.Rate(r.id(), r.baseCurrency(), r.quoteCurrency(), r.rate(), r.rateDate(),
					r.source(), r.createdAt()))
			.toList();
	}

	@Transactional
	ApprovalViews.Rate addRate(CurrentUser admin, RateInput input) {
		Absences.requireAdmin(admin);
		List<FieldViolation> errors = new ArrayList<>();
		if (input.baseCurrency() == null || !rates.currencyExists(input.baseCurrency())) {
			errors.add(FieldViolation.of("baseCurrency", "invalid-value"));
		}
		if (input.quoteCurrency() == null || !rates.currencyExists(input.quoteCurrency())) {
			errors.add(FieldViolation.of("quoteCurrency", "invalid-value"));
		}
		if (input.baseCurrency() != null && input.baseCurrency().equals(input.quoteCurrency())) {
			errors.add(FieldViolation.of("quoteCurrency", "invalid-value"));
		}
		if (input.rate() == null || input.rate().signum() <= 0) {
			errors.add(FieldViolation.of("rate", "invalid-value"));
		}
		else if (input.rate().stripTrailingZeros().scale() > 8 || input.rate().precision() - input.rate().scale() > 11) {
			errors.add(FieldViolation.of("rate", "max-decimals", "max", 8));
		}
		if (input.rateDate() == null) {
			errors.add(FieldViolation.of("rateDate", "required"));
		}
		if (!StringUtils.hasText(input.source())) {
			errors.add(FieldViolation.of("source", "required"));
		}
		else if (input.source().length() > 60) {
			errors.add(FieldViolation.of("source", "too-long", "max", 60));
		}
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
		long id = rates.add(admin.groupId(), input.baseCurrency(), input.quoteCurrency(), input.rate(), input.rateDate(),
				input.source().strip(), admin.id(), clock.instant());
		return rates(admin).stream().filter(r -> r.id() == id).findFirst().orElseThrow();
	}

	private ApprovalStore.Rule ruleOfGroup(CurrentUser admin, long id) {
		Absences.requireAdmin(admin);
		return store.rule(id).filter(r -> r.groupId() == admin.groupId()).orElseThrow(ApiProblemException::notFound);
	}

	private List<ApprovalStore.Stage> validate(CurrentUser admin, RuleInput input) {
		List<FieldViolation> errors = new ArrayList<>();
		if (input.companyId() == null || organization.findCompany(input.companyId())
			.filter(c -> c.groupId() == admin.groupId())
			.isEmpty()) {
			errors.add(FieldViolation.of("companyId", "company-not-allowed"));
		}
		if (!StringUtils.hasText(input.name())) {
			errors.add(FieldViolation.of("name", "required"));
		}
		else if (input.name().length() > 150) {
			errors.add(FieldViolation.of("name", "too-long", "max", 150));
		}
		if (input.categoryId() != null && !store.categoryExists(admin.groupId(), input.categoryId())) {
			errors.add(FieldViolation.of("categoryId", "invalid-value"));
		}
		if (input.purchaseType() != null && !Set.of("LOCAL", "INTERNATIONAL").contains(input.purchaseType())) {
			errors.add(FieldViolation.of("purchaseType", "invalid-value"));
		}
		BigDecimal min = min(input);
		if (min.signum() < 0) {
			errors.add(FieldViolation.of("minAmount", "invalid-value"));
		}
		if (input.maxAmount() != null && input.maxAmount().compareTo(min) <= 0) {
			errors.add(FieldViolation.of("maxAmount", "invalid-value"));
		}
		List<ApprovalStore.Stage> stages = new ArrayList<>();
		if (input.stages() == null || input.stages().isEmpty()) {
			errors.add(FieldViolation.of("stages", "required"));
		}
		else if (input.stages().size() > 10) {
			errors.add(FieldViolation.of("stages", "too-many-lines", "max", 10));
		}
		else {
			for (int i = 0; i < input.stages().size(); i++) {
				StageInput stage = input.stages().get(i);
				String path = "stages[" + i + "].";
				if (!StringUtils.hasText(stage.name()) || stage.name().length() > 100) {
					errors.add(FieldViolation.of(path + "name", StringUtils.hasText(stage.name()) ? "too-long" : "required"));
				}
				if (stage.approverType() == null || !TYPES.contains(stage.approverType())) {
					errors.add(FieldViolation.of(path + "approverType", "invalid-value"));
				}
				else if ("NAMED_USER".equals(stage.approverType())) {
					boolean valid = stage.approverUserId() != null && users.findContact(stage.approverUserId())
						.filter(c -> c.active() && c.groupId() == admin.groupId())
						.isPresent();
					if (!valid) {
						errors.add(FieldViolation.of(path + "approverUserId", "invalid-value"));
					}
				}
				if (stage.policy() == null || !Set.of("ANY", "ALL").contains(stage.policy())) {
					errors.add(FieldViolation.of(path + "policy", "invalid-value"));
				}
				if (stage.slaHours() == null || stage.slaHours() < 1 || stage.slaHours() > 720) {
					errors.add(FieldViolation.of(path + "slaHours", "invalid-value"));
				}
				if (stage.escalationRole() != null && !ESCALATION_ROLES.contains(stage.escalationRole())) {
					errors.add(FieldViolation.of(path + "escalationRole", "invalid-value"));
				}
				if (stage.minAmount() != null && stage.minAmount().signum() < 0) {
					errors.add(FieldViolation.of(path + "minAmount", "invalid-value"));
				}
				stages.add(new ApprovalStore.Stage(i + 1, stage.name() == null ? "" : stage.name().strip(),
						stage.approverType(), "NAMED_USER".equals(stage.approverType()) ? stage.approverUserId() : null,
						stage.policy(), stage.slaHours() == null ? 0 : stage.slaHours(), stage.escalationRole(),
						stage.minAmount()));
			}
		}
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
		return stages;
	}

	private ApprovalViews.Rule view(ApprovalStore.Rule rule) {
		ApprovalViews.Ref company = organization.findCompany(rule.companyId())
			.map(c -> new ApprovalViews.Ref(c.id(), c.code(), c.name()))
			.orElse(new ApprovalViews.Ref(rule.companyId(), "", ""));
		return new ApprovalViews.Rule(rule.id(), rule.ruleKey(), rule.versionNo(), rule.status(), company, rule.name(),
				rule.categoryId(), rule.purchaseType(), rule.minAmount(), rule.maxAmount(), rule.createdAt(),
				rule.activatedAt(), store.ruleUsed(rule.id()),
				rule.stages()
					.stream()
					.map(s -> new ApprovalViews.RuleStage(s.stageNo(), s.name(), s.approverType(), s.approverUserId(),
							s.policy(), s.slaHours(), s.escalationRole(), s.minAmount()))
					.toList());
	}

	private static BigDecimal min(RuleInput input) {
		return input.minAmount() == null ? BigDecimal.ZERO : input.minAmount();
	}

	record StageInput(String name, String approverType, Long approverUserId, String policy, Integer slaHours,
			String escalationRole, BigDecimal minAmount) {
	}

	record RuleInput(Long companyId, String name, Long categoryId, String purchaseType, BigDecimal minAmount,
			BigDecimal maxAmount, List<StageInput> stages) {
	}

	record RateInput(String baseCurrency, String quoteCurrency, BigDecimal rate, java.time.LocalDate rateDate,
			String source) {
	}

}
