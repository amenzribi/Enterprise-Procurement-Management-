package com.epi.procurement.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.RoleGrant;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.organization.PurchasingMode;
import com.epi.procurement.platform.persistence.SqlServerErrors;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;

/**
 * Drafting, submission and lookup of purchase requests. The acting user always comes from the session
 * ({@link CurrentUser}); every lookup goes through {@link ScopedRequests}.
 * <p>
 * Audit (S1DR-20): draft creation, submission and discard are chained audit events written in the same transaction
 * as the change. Draft edits are not chained (low audit value, and they would serialise every save of a company on
 * the chain lock); they are tracked by {@code updated_by}/{@code updated_at}.
 */
@Service
class RequestService {

	private static final Logger log = LoggerFactory.getLogger(RequestService.class);

	private static final int SUBMIT_ATTEMPTS = 3;

	private static final String ENTITY = "PURCHASE_REQUEST";

	private final ScopedRequests requests;

	private final RequestTransitionRepository transitions;

	private final RequestAccessPolicy policy;

	private final RequestValidator validator;

	private final RequestReferenceData reference;

	private final RequestNumbering numbering;

	private final RequestMapper mapper;

	private final OrganizationDirectory organization;

	private final UserDirectory users;

	private final AuditLog auditLog;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	private final int maxLines;

	RequestService(ScopedRequests requests, RequestTransitionRepository transitions, RequestAccessPolicy policy,
			RequestValidator validator, RequestReferenceData reference, RequestNumbering numbering,
			RequestMapper mapper, OrganizationDirectory organization, UserDirectory users, AuditLog auditLog,
			PlatformTransactionManager transactionManager, ObjectProvider<Clock> clock, ApplicationEventPublisher events,
			@Value("${app.request.max-lines:100}") int maxLines) {
		this.requests = requests;
		this.transitions = transitions;
		this.policy = policy;
		this.validator = validator;
		this.reference = reference;
		this.numbering = numbering;
		this.mapper = mapper;
		this.organization = organization;
		this.users = users;
		this.auditLog = auditLog;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.events = events;
		this.maxLines = maxLines;
	}

	@Transactional(readOnly = true)
	RequestViews.Page<RequestViews.Summary> search(CurrentUser user, Specification<PurchaseRequest> filter,
			Pageable pageable) {
		Page<PurchaseRequest> page = requests.search(user, filter, pageable);
		return new RequestViews.Page<>(mapper.summaries(user, page.getContent()), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	@Transactional(readOnly = true)
	RequestViews.Detail get(CurrentUser user, long id) {
		return mapper.detail(user, requests.getVisible(user, id));
	}

	@Transactional
	RequestViews.Detail create(CurrentUser user, RequestPayload payload) {
		OrganizationDirectory.CompanyRef company = requestingCompany(user, payload.requestingCompanyId());
		if (!policy.canCreateIn(user, company.id())) {
			throw ApiProblemException.forbidden();
		}
		validate(validator.validateDraft(user, company, payload, maxLines));
		Instant now = clock.instant();
		long requester = payload.requesterId() != null ? payload.requesterId() : user.id();
		String currency = StringUtils.hasText(payload.currency()) ? payload.currency() : company.currencyCode();
		PurchaseRequest request = new PurchaseRequest(company.groupId(), company.id(), company.id(),
				buyingCompany(company), currency, requester, user.id(), now);
		apply(request, payload, currency);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(request.getId(), null, RequestStatus.DRAFT, requester, user.id(), now));
		auditLog.record(new AuditRecord(user.id(), user.displayName(), company.id(), ENTITY,
				request.getId().toString(), "DRAFT_CREATED", null, RequestStatus.DRAFT.name(), null, null));
		return mapper.detail(user, request);
	}

	@Transactional
	RequestViews.Detail update(CurrentUser user, long id, RequestPayload payload) {
		PurchaseRequest request = requests.getVisible(user, id);
		requireEditable(user, request);
		requireVersion(payload.version(), request);
		// An omitted requester means "unchanged" on update; only creation defaults to the signed-in user (S1CR-04).
		if (payload.requesterId() == null) {
			payload = payload.withRequesterId(request.getRequesterUserId());
		}

		OrganizationDirectory.CompanyRef company = requestingCompany(user,
				payload.requestingCompanyId() != null ? payload.requestingCompanyId() : request.getRequestingCompanyId());
		if (!policy.canCreateIn(user, company.id())) {
			throw ApiProblemException.forbidden();
		}
		validate(validator.validateDraft(user, company, payload, maxLines));
		Instant now = clock.instant();
		if (company.id() != request.getRequestingCompanyId()) {
			request.setCompanies(company.id(), company.id(), buyingCompany(company));
		}
		String currency = StringUtils.hasText(payload.currency()) ? payload.currency() : company.currencyCode();
		long requester = payload.requesterId();
		request.setRequesterUserId(requester);
		request.assignOwner(requester, now);
		apply(request, payload, currency);
		// Always changes the header, so every save - including line-only edits - increments the version (S1DR-06).
		request.touch(user.id(), now);
		requests.saveAndFlush(request);
		return mapper.detail(user, request);
	}

	/**
	 * Submits a draft (number, DRAFT -> IN_APPROVAL) or resubmits a returned request (number kept, new round), with
	 * the approval circuit, stage timeline and audit event in one transaction. Locks are taken in a fixed order -
	 * request row, document counter, approval round and tasks, audit chain head (S1DR-10, S2DR-08) - and the whole transaction is
	 * retried, in a new transaction, only when SQL Server chose it as a deadlock victim (error 1205). Lock timeouts
	 * are not retried: they fail fast with 503 {@code busy} (S1CR-03).
	 */
	RequestViews.Detail submit(CurrentUser user, long id, Integer version) {
		for (int attempt = 1;; attempt++) {
			try {
				return transaction.execute(status -> doSubmit(user, id, version));
			}
			catch (DataAccessException ex) {
				if (!SqlServerErrors.isDeadlockVictim(ex) || attempt >= SUBMIT_ATTEMPTS) {
					throw ex;
				}
				log.warn("Submission of request {} retried after {} (attempt {})", id, ex.getClass().getSimpleName(),
						attempt);
			}
		}
	}

	@Transactional
	void discard(CurrentUser user, long id, Integer version) {
		// Request row first, then the audit chain: the same order as submission.
		PurchaseRequest request = requests.getVisibleForUpdate(user, id);
		requireEditable(user, request);
		if (request.getStatus() != RequestStatus.DRAFT) {
			throw ApiProblemException.conflict("invalid-state", "Only a draft can be discarded.");
		}
		requireVersion(version, request);
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.getRequestingCompanyId(), ENTITY,
				request.getId().toString(), "DRAFT_DISCARDED", RequestStatus.DRAFT.name(), null, null, null));
		requests.delete(request);
	}

	@Transactional(readOnly = true)
	RequestViews.FormOptions formOptions(CurrentUser user) {
		List<RequestViews.CompanyOption> companies = new ArrayList<>();
		for (long companyId : creatableCompanies(user)) {
			organization.findCompany(companyId).filter(c -> c.groupId() == user.groupId()).ifPresent(company -> {
				boolean onBehalf = policy.canEnterOnBehalfIn(user, company.id());
				List<RequestViews.RequesterOption> requesters = onBehalf
						? users.activeUsersOfCompany(company.id())
							.stream()
							.map(u -> new RequestViews.RequesterOption(u.id(), u.displayName(), u.departmentId()))
							.toList()
						: List.of(new RequestViews.RequesterOption(user.id(), user.displayName(), user.departmentId()));
				RequestViews.Ref self = new RequestViews.Ref(company.id(), company.code(), company.name());
				long buyingId = buyingCompany(company);
				RequestViews.Ref buying = buyingId == company.id() ? self
						: organization.findCompany(buyingId)
							.map(c -> new RequestViews.Ref(c.id(), c.code(), c.name()))
							.orElse(self);
				companies.add(new RequestViews.CompanyOption(company.id(), company.code(), company.name(),
						company.currencyCode(), company.timeZone(), onBehalf,
						organization.departments(company.id())
							.stream()
							.map(d -> new RequestViews.DepartmentOption(d.id(), d.code(), d.name(), d.siteId()))
							.toList(),
						refs(organization.sites(company.id())), refs(organization.costCenters(company.id())), requesters,
						self, buying, true));
			});
		}
		RequestViews.CompanyOption home = companies.stream()
			.filter(c -> c.id() == user.companyId())
			.findFirst()
			.orElse(companies.isEmpty() ? null : companies.get(0));
		RequestViews.Defaults defaults = new RequestViews.Defaults(home == null ? null : home.id(),
				home != null && home.id() == user.companyId() ? user.departmentId() : null,
				home != null && home.id() == user.companyId() ? user.siteId() : null,
				home == null ? null : home.currency(), user.id());
		return new RequestViews.FormOptions(
				reference.currencies()
					.stream()
					.map(c -> new RequestViews.CurrencyOption(c.code(), c.nameEn(), c.nameFr(), c.minorUnits()))
					.toList(),
				reference.units().stream().map(u -> new RequestViews.UnitOption(u.code(), u.nameEn(), u.nameFr())).toList(),
				reference.activeCategories(user.groupId())
					.stream()
					.map(c -> new RequestViews.LocalizedRef(c.id(), c.code(), c.nameEn(), c.nameFr()))
					.toList(),
				names(Priority.values()), names(PurchaseType.values()), maxLines, defaults, companies);
	}

	private RequestViews.Detail doSubmit(CurrentUser user, long id, Integer version) {
		PurchaseRequest request = requests.getVisibleForUpdate(user, id);
		requireEditable(user, request);
		requireVersion(version, request);
		OrganizationDirectory.CompanyRef company = organization.findCompany(request.getRequestingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ZoneId.of(company.timeZone()));
		validate(validator.validateSubmission(request, today));

		RequestStatus from = request.getStatus();
		boolean resubmission = from == RequestStatus.REVISION_REQUESTED;
		if (!resubmission) {
			request.markSubmitted(numbering.next(company.id(), company.code(), today.getYear()), now);
		}
		int round = request.nextRound();
		request.transitionTo(RequestStatus.IN_APPROVAL, request.getRequesterUserId(), now);
		request.getLines().forEach(line -> line.setStatus(RequestStatus.CREATED));
		request.touch(user.id(), now);
		requests.saveAndFlush(request);
		transitions.save(new RequestTransition(request.getId(), from, RequestStatus.IN_APPROVAL,
				request.getCurrentOwnerUserId(), user.id(), now));
		// The approval module starts the circuit in this transaction (rule, rate, round, tasks, notifications);
		// any failure (no rule, missing rate, no eligible approver) rolls the whole submission back.
		events.publishEvent(new RequestSubmittedEvent(request.getId(), round, user.id(), now, resubmission));
		auditLog.record(new AuditRecord(user.id(), user.displayName(), company.id(), ENTITY, request.getId().toString(),
				resubmission ? "RESUBMITTED" : "SUBMITTED", from.name(), RequestStatus.IN_APPROVAL.name(), null,
				"{\"number\":\"" + request.getNumber() + "\",\"round\":" + round + ",\"totalAmount\":\""
						+ request.getTotalAmount().toPlainString() + "\",\"currency\":\"" + request.getCurrencyCode()
						+ "\"}"));
		return mapper.detail(user, request);
	}

	/**
	 * Copies the payload onto the draft, reusing lines by position so line numbers never collide, and recomputes
	 * totals: line total = round(quantity x price) to the currency's minor units, request total = sum of rounded
	 * line totals (S1DR-17).
	 */
	private void apply(PurchaseRequest request, RequestPayload p, String currency) {
		int minorUnits = reference.currency(currency).map(RequestReferenceData.Currency::minorUnits).orElse(2);
		List<BigDecimal> lineTotals = new ArrayList<>();
		List<FieldViolation> errors = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (int i = 0; i < p.lines().size(); i++) {
			RequestPayload.LinePayload line = p.lines().get(i);
			BigDecimal lineTotal = line.quantity() == null || line.estimatedUnitPrice() == null ? BigDecimal.ZERO
					: line.quantity().multiply(line.estimatedUnitPrice()).setScale(minorUnits, RoundingMode.HALF_UP);
			if (RequestValidator.exceedsIntegerDigits(lineTotal)) {
				errors.add(FieldViolation.of("lines[" + i + "].estimatedUnitPrice", "too-large"));
			}
			lineTotals.add(lineTotal);
			total = total.add(lineTotal);
		}
		if (RequestValidator.exceedsIntegerDigits(total)) {
			errors.add(FieldViolation.of("lines", "too-large"));
		}
		Set<Long> knownLineIds = new HashSet<>();
		request.getLines().forEach(line -> {
			if (line.getId() != null) {
				knownLineIds.add(line.getId());
			}
		});
		Set<Long> seenLineIds = new HashSet<>();
		for (int i = 0; i < p.lines().size(); i++) {
			Long lineId = p.lines().get(i).id();
			if (lineId != null && (!knownLineIds.contains(lineId) || !seenLineIds.add(lineId))) {
				// Unknown here (another request's line, a deleted line) or sent twice.
				errors.add(FieldViolation.of("lines[" + i + "].id", "invalid-value"));
			}
		}
		validate(errors);

		request.setTitle(clean(p.title()));
		request.setCategoryId(p.categoryId());
		request.setPriority(RequestValidator.parse(Priority.class, p.priority()).orElse(null));
		request.setPurchaseType(RequestValidator.parse(PurchaseType.class, p.purchaseType()).orElse(null));
		request.setCurrencyCode(currency);
		request.setDepartmentId(p.departmentId());
		request.setSiteId(p.siteId());
		request.setCostCenterId(p.costCenterId());
		request.setBeneficiary(clean(p.beneficiary()));
		request.setBeneficiarySiteId(p.beneficiarySiteId());
		request.setProject(clean(p.project()));
		request.setJustification(clean(p.justification()));
		request.setUrgencyJustification(clean(p.urgencyJustification()));
		request.setBudgetLineRef(clean(p.budgetLineRef()));
		request.setEquipmentRef(clean(p.equipmentRef()));
		request.setMaintenanceOrderRef(clean(p.maintenanceOrderRef()));
		request.setRequiredDate(p.requiredDate());
		request.setDeliveryLocation(clean(p.deliveryLocation()));

		// Match by id (S1CR-07): kept lines keep their number, new lines take the next free number, lines whose id
		// is not sent are removed. Numbers of removed lines are not reused within the same save.
		List<PurchaseRequestLine> lines = request.getLines();
		Map<Long, PurchaseRequestLine> existing = new HashMap<>();
		int nextLineNo = 1;
		for (PurchaseRequestLine line : lines) {
			if (line.getId() != null) {
				existing.put(line.getId(), line);
			}
			nextLineNo = Math.max(nextLineNo, line.getLineNo() + 1);
		}
		Set<PurchaseRequestLine> kept = new HashSet<>();
		for (int i = 0; i < p.lines().size(); i++) {
			RequestPayload.LinePayload source = p.lines().get(i);
			PurchaseRequestLine line = source.id() == null ? null : existing.get(source.id());
			if (line == null) {
				line = new PurchaseRequestLine(request, (short) nextLineNo++);
				lines.add(line);
			}
			kept.add(line);
			line.setDescription(clean(source.description()));
			line.setItemReference(clean(source.itemReference()));
			line.setQuantity(source.quantity());
			line.setUnitCode(clean(source.unit()));
			line.setEstimatedUnitPrice(source.estimatedUnitPrice());
			line.setLineTotal(lineTotals.get(i));
			line.setRequiredDate(source.requiredDate());
			line.setDeliveryLocation(clean(source.deliveryLocation()));
		}
		lines.removeIf(line -> !kept.contains(line));
		lines.sort(Comparator.comparingInt(PurchaseRequestLine::getLineNo));
		request.setTotalAmount(total);
	}

	/**
	 * The requesting company is the user's own company unless they hold ENTER_ON_BEHALF in another company of their
	 * group (S1DR-15).
	 */
	private OrganizationDirectory.CompanyRef requestingCompany(CurrentUser user, Long requested) {
		long companyId = requested != null ? requested : user.companyId();
		boolean allowed = companyId == user.companyId() || policy.canEnterOnBehalfIn(user, companyId);
		return organization.findCompany(companyId)
			.filter(c -> allowed && c.groupId() == user.groupId())
			.orElseThrow(() -> new ValidationFailedException(
					List.of(FieldViolation.of("requestingCompanyId", "company-not-allowed"))));
	}

	/**
	 * Proposed buying company from the company's purchasing mode (S1DR-07): the group's purchasing centre for
	 * CENTRALIZED, otherwise the requesting company itself (DIRECT, ON_BEHALF - where the subsidiary remains the
	 * legal buyer - and MIXED until category rules exist). Purchasing confirms or changes it in Sprint 3 (US-15).
	 */
	private long buyingCompany(OrganizationDirectory.CompanyRef company) {
		if (company.purchasingMode() == PurchasingMode.CENTRALIZED) {
			return organization.purchasingCenterOf(company.groupId())
				.map(OrganizationDirectory.CompanyRef::id)
				.orElse(company.id());
		}
		return company.id();
	}

	private Set<Long> creatableCompanies(CurrentUser user) {
		Set<Long> candidates = new LinkedHashSet<>();
		candidates.add(user.companyId());
		for (RoleGrant grant : user.grants()) {
			if ((grant.role() == Role.REQUESTER || grant.role() == Role.ENTER_ON_BEHALF) && grant.companyId() != null) {
				candidates.add(grant.companyId());
			}
		}
		candidates.removeIf(id -> !policy.canCreateIn(user, id));
		return candidates;
	}

	/**
	 * Permission before state (QA1-D-02): a user who can see the request but is not its editor gets 403 whatever the
	 * status; an editor gets 409 {@code invalid-state} once the request is no longer a draft.
	 */
	private void requireEditable(CurrentUser user, PurchaseRequest request) {
		if (!policy.isEditor(user, request)) {
			throw ApiProblemException.forbidden();
		}
		if (!RequestAccessPolicy.isEditableStatus(request.getStatus())) {
			throw ApiProblemException.conflict("invalid-state",
					"Only a draft or a request returned for revision can be changed or submitted.");
		}
	}

	private static void requireVersion(Integer version, PurchaseRequest request) {
		if (version == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("version", "required")));
		}
		if (version != request.getVersion()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The request was changed by someone else. Reload it and try again.");
		}
	}

	private static void validate(List<FieldViolation> errors) {
		if (!errors.isEmpty()) {
			throw new ValidationFailedException(errors);
		}
	}

	private static String clean(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}

	private static List<RequestViews.Ref> refs(List<OrganizationDirectory.UnitRef> units) {
		return units.stream().map(u -> new RequestViews.Ref(u.id(), u.code(), u.name())).toList();
	}

	private static List<String> names(Enum<?>[] values) {
		return java.util.Arrays.stream(values).map(Enum::name).toList();
	}

}
