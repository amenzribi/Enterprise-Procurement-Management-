package com.epi.procurement.purchasing;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.Role;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.notification.Notice;
import com.epi.procurement.notification.Notifications;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.organization.PurchasingMode;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

@Service
class PurchasingService {

	private static final String ENTITY = "PURCHASE_REQUEST";

	private final RequestWorkflow requests;

	private final BuyerAssignmentStore assignments;

	private final OrganizationDirectory organization;

	private final UserDirectory users;

	private final AuditLog auditLog;

	private final Notifications notifications;

	private final Clock clock;

	private final JdbcTemplate jdbc;

	PurchasingService(RequestWorkflow requests, BuyerAssignmentStore assignments, OrganizationDirectory organization,
			UserDirectory users, AuditLog auditLog, Notifications notifications, ObjectProvider<Clock> clock,
			DataSource dataSource) {
		this.requests = requests;
		this.assignments = assignments;
		this.organization = organization;
		this.users = users;
		this.auditLog = auditLog;
		this.notifications = notifications;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Transactional(readOnly = true)
	QueuePage queue(CurrentUser user, QueueFilter filter) {
		requirePurchasing(user);
		Page<RequestSnapshot> page = requests.purchasingQueue(user, toQuery(user, filter));
		List<QueueItem> items = page.getContent().stream().map(this::item).toList();
		return new QueuePage(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	@Transactional(readOnly = true)
	Map<String, Long> count(CurrentUser user) {
		requirePurchasing(user);
		long n = requests.purchasingQueue(user, new RequestWorkflow.PurchasingQuery(null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, "requiredDate", false, 0, 1)).getTotalElements();
		return Map.of("pending", n);
	}

	private RequestWorkflow.PurchasingQuery toQuery(CurrentUser user, QueueFilter filter) {
		java.util.Collection<Long> requestingIds = null;
		if (StringUtils.hasText(filter.countryCode())) {
			requestingIds = jdbc.queryForList(
					"SELECT id FROM company WHERE group_id = ? AND UPPER(country_code) = ? AND is_active = 1", Long.class,
					user.groupId(), filter.countryCode().strip().toUpperCase(java.util.Locale.ROOT));
		}
		return new RequestWorkflow.PurchasingQuery(filter.q(), filter.buyingCompanyId(), filter.categoryId(),
				filter.status(), filter.priority(), filter.purchaseType(), filter.purchaseMode(), filter.buyerUserId(),
				filter.requesterUserId(), filter.requiredFrom(), filter.requiredTo(), requestingIds, filter.amountMin(),
				filter.amountMax(), filter.complete(), filter.sort(), filter.descending(), filter.page(), filter.size());
	}

	@Transactional(readOnly = true)
	FilterOptions filterOptions(CurrentUser user) {
		requirePurchasing(user);
		List<Ref> companies = organization.companiesOfGroup(user.groupId())
			.stream()
			.map(c -> new Ref(c.id(), c.code(), c.name()))
			.toList();
		List<Ref> categories = jdbc.query("""
				SELECT id, code, name_en FROM request_category WHERE group_id = ? AND is_active = 1 ORDER BY code""",
				(rs, i) -> new Ref(rs.getLong("id"), rs.getString("code"), rs.getString("name_en")), user.groupId());
		Set<Long> buyerCompanyIds = new HashSet<>();
		user.grants(Role.BUYER).forEach(g -> {
			if (g.companyId() != null) {
				buyerCompanyIds.add(g.companyId());
			}
		});
		user.grants(Role.PURCHASING_MANAGER).forEach(g -> {
			if (g.companyId() != null) {
				buyerCompanyIds.add(g.companyId());
			}
		});
		List<Ref> buyers = buyerCompanyIds.stream()
			.flatMap(companyId -> users.searchUsers(user.groupId(), companyId, Role.BUYER.name(), null).stream())
			.map(u -> new Ref(u.id(), "", u.displayName()))
			.distinct()
			.toList();
		List<Ref> requesters = users.searchUsers(user.groupId(), null, Role.REQUESTER.name(), null)
			.stream()
			.map(u -> new Ref(u.id(), "", u.displayName()))
			.toList();
		List<String> countries = jdbc.queryForList(
				"SELECT DISTINCT country_code FROM company WHERE group_id = ? AND is_active = 1 ORDER BY country_code",
				String.class, user.groupId());
		return new FilterOptions(companies, categories, buyers, requesters, countries);
	}

	@Transactional(readOnly = true)
	List<BuyerOption> buyers(CurrentUser user, long companyId, Long categoryId) {
		requireManagerOf(user, companyId);
		List<UserDirectory.UserListing> found = users.searchUsers(user.groupId(), companyId, Role.BUYER.name(), null);
		List<Long> ids = found.stream().map(UserDirectory.UserListing::id).toList();
		Map<Long, Integer> load = assignments.openWorkload(ids);
		Set<Long> affinity = categoryId == null ? Set.of()
				: new HashSet<>(assignments.buyersWithAffinity(companyId, categoryId));
		Long suggested = found.stream()
			.filter(u -> affinity.isEmpty() || affinity.contains(u.id()))
			.min((a, b) -> Integer.compare(load.getOrDefault(a.id(), 0), load.getOrDefault(b.id(), 0)))
			.map(UserDirectory.UserListing::id)
			.or(() -> found.stream()
				.min((a, b) -> Integer.compare(load.getOrDefault(a.id(), 0), load.getOrDefault(b.id(), 0)))
				.map(UserDirectory.UserListing::id))
			.orElse(null);
		return found.stream()
			.map(u -> new BuyerOption(u.id(), u.displayName(), u.email(), load.getOrDefault(u.id(), 0),
					Objects.equals(suggested, u.id())))
			.toList();
	}

	@Transactional(readOnly = true)
	List<Assignment> history(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		requirePurchasingOn(user, request);
		Map<Long, UserDirectory.UserContact> people = users.findContacts(assignments.history(requestId)
			.stream()
			.flatMap(r -> java.util.stream.Stream.of(r.buyerUserId(), r.assignedByUserId()))
			.distinct()
			.toList());
		return assignments.history(requestId)
			.stream()
			.map(r -> new Assignment(r.id(), r.buyerUserId(), name(people, r.buyerUserId()), r.assignedByUserId(),
					name(people, r.assignedByUserId()), r.status(), r.reason(), r.assignedAt(), r.decidedAt()))
			.toList();
	}

	@Transactional
	QueueItem assign(CurrentUser user, long requestId, Integer version, Long buyerUserId, String purchaseMode) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireVersion(version, request);
		requireManagerOf(user, request.buyingCompanyId());
		if (buyerUserId == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("buyerUserId", "required")));
		}
		if (!"IN_PURCHASING_REVIEW".equals(request.status()) && !"ASSIGNED_TO_BUYER".equals(request.status())) {
			throw ApiProblemException.conflict("invalid-state", "The request is not waiting for a buyer.");
		}
		if (users.searchUsers(user.groupId(), request.buyingCompanyId(), Role.BUYER.name(), null)
			.stream()
			.noneMatch(u -> u.id() == buyerUserId)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("buyerUserId", "not-eligible")));
		}
		// Separation of duties (F-5): whoever asked for the purchase does not buy it.
		if (request.isAuthor(buyerUserId)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("buyerUserId", "buyer-is-requester")));
		}
		Instant now = clock.instant();
		if (StringUtils.hasText(purchaseMode)) {
			applyMode(user, request, purchaseMode, now);
			request = requests.currentLocked(requestId);
		}
		assignments.supersedeOpen(requestId, now);
		assignments.insert(requestId, request.buyingCompanyId(), buyerUserId, user.id(), now);
		requests.assignBuyer(requestId, buyerUserId, now, user.id());
		request = requests.currentLocked(requestId);
		audit(user, request, "BUYER_ASSIGNED", request.status(), null,
				"{\"buyerUserId\":" + buyerUserId + "}");
		notify(request, buyerUserId, "BUYER_ASSIGNED", user);
		notifyAuthors(request, "BUYER_ASSIGNED", user);
		return item(request);
	}

	@Transactional
	QueueItem accept(CurrentUser user, long requestId, Integer version) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireVersion(version, request);
		BuyerAssignmentStore.Row current = assignments.current(requestId)
			.orElseThrow(() -> ApiProblemException.conflict("invalid-state", "No assignment is waiting."));
		if (current.buyerUserId() != user.id()) {
			throw ApiProblemException.forbidden();
		}
		// An assignment made before the rule existed cannot be accepted by the requester either (F-5).
		if (request.isAuthor(user.id())) {
			throw ApiProblemException.conflict("buyer-is-requester", "You cannot buy for your own request.");
		}
		if (!"ASSIGNED".equals(current.status())) {
			throw ApiProblemException.conflict("invalid-state", "No assignment is waiting.");
		}
		Instant now = clock.instant();
		assignments.decide(current.id(), "ACCEPTED", null, now);
		requests.buyerAccepted(requestId, user.id(), now);
		request = requests.currentLocked(requestId);
		audit(user, request, "BUYER_ACCEPTED", request.status(), null, null);
		notifyAuthors(request, "BUYER_ACCEPTED", user);
		return item(request);
	}

	@Transactional
	QueueItem returnAssignment(CurrentUser user, long requestId, Integer version, String reason) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireVersion(version, request);
		if (!StringUtils.hasText(reason)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("reason", "required")));
		}
		BuyerAssignmentStore.Row current = assignments.current(requestId)
			.orElseThrow(() -> ApiProblemException.conflict("invalid-state", "No assignment is waiting."));
		if (current.buyerUserId() != user.id() && !user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())) {
			throw ApiProblemException.forbidden();
		}
		Instant now = clock.instant();
		assignments.decide(current.id(), "RETURNED", reason.strip(), now);
		requests.clearBuyer(requestId, user.id(), now);
		request = requests.currentLocked(requestId);
		audit(user, request, "BUYER_RETURNED", request.status(), reason.strip(), null);
		notifyAuthors(request, "BUYER_RETURNED", user);
		return item(request);
	}

	@Transactional
	QueueItem purchaseMode(CurrentUser user, long requestId, Integer version, String mode) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireVersion(version, request);
		requireManagerOf(user, request.buyingCompanyId());
		Instant now = clock.instant();
		applyMode(user, request, mode, now);
		return item(requests.currentLocked(requestId));
	}

	/** Records purchasing's decision on the service lines; the requester who chose the type cannot make it (D-10). */
	@Transactional
	QueueItem decideLineClassification(CurrentUser user, long requestId, ClassificationBody body) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requirePurchasingOn(user, request);
		if (request.isAuthor(user.id())) {
			throw new ApiProblemException(org.springframework.http.HttpStatus.FORBIDDEN, "own-classification",
					"Another person must decide the classification of your own request.");
		}
		requireVersion(body.version(), request);
		List<Long> toGoods = body.reclassifyAsGoods() == null ? List.of() : body.reclassifyAsGoods();
		Instant now = clock.instant();
		requests.decideLineClassification(requestId, toGoods, user.id(), now);
		RequestSnapshot after = requests.currentLocked(requestId);
		audit(user, after, "LINE_CLASSIFICATION_DECIDED", after.status(), StringUtils.hasText(body.comment()) ? body.comment().strip() : null,
				"{\"reclassifiedAsGoods\":" + toGoods + "}");
		return item(after);
	}

	@Transactional
	QueueItem completenessReturn(CurrentUser user, long requestId, Integer version, String reason) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireVersion(version, request);
		requirePurchasingOn(user, request);
		if (!StringUtils.hasText(reason)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("reason", "required")));
		}
		Instant now = clock.instant();
		requests.returnFromPurchasing(requestId, user.id(), now, reason.strip());
		request = requests.currentLocked(requestId);
		audit(user, request, "PURCHASING_RETURNED", request.status(), reason.strip(), null);
		notifyAuthors(request, "PURCHASING_RETURNED", user);
		return item(request);
	}

	/**
	 * Purchasing question to the requester (S3-AC-02): SLA-free, no status change, does not restart approval.
	 */
	@Transactional
	QueueItem askQuestion(CurrentUser user, long requestId, Integer version, String question) {
		RequestSnapshot request = requests.requireVisibleForUpdate(user, requestId);
		requireVersion(version, request);
		requirePurchasingOn(user, request);
		if (!"IN_PURCHASING_REVIEW".equals(request.status()) && !"ASSIGNED_TO_BUYER".equals(request.status())) {
			throw ApiProblemException.conflict("invalid-state", "The request is not in purchasing.");
		}
		if (!StringUtils.hasText(question)) {
			throw new ValidationFailedException(List.of(FieldViolation.of("question", "required")));
		}
		String clean = question.strip();
		if (clean.length() > 2000) {
			throw new ValidationFailedException(List.of(FieldViolation.of("question", "too-long")));
		}
		audit(user, request, "PURCHASING_QUESTION", request.status(), clean, null);
		notifications.enqueue(new Notice("PURCHASING_QUESTION", request.requesterUserId(), request.id(),
				Map.of("number", nullToEmpty(request.number()), "title", nullToEmpty(request.title()), "actor",
						user.displayName(), "question", clean),
				List.of(), List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
				"/requests/" + request.id()));
		if (request.enteredByUserId() != request.requesterUserId()) {
			notifications.enqueue(new Notice("PURCHASING_QUESTION", request.enteredByUserId(), request.id(),
					Map.of("number", nullToEmpty(request.number()), "title", nullToEmpty(request.title()), "actor",
							user.displayName(), "question", clean),
					List.of(), List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
					"/requests/" + request.id()));
		}
		return item(request);
	}

	private void applyMode(CurrentUser user, RequestSnapshot request, String modeCode, Instant now) {
		PurchasingMode mode;
		try {
			mode = PurchasingMode.valueOf(modeCode);
		}
		catch (IllegalArgumentException ex) {
			throw ApiProblemException.invalidParameter("purchaseMode");
		}
		if (mode == PurchasingMode.MIXED) {
			throw ApiProblemException.invalidParameter("purchaseMode");
		}
		OrganizationDirectory.CompanyRef requesting = organization.findCompany(request.requestingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		PurchasingMode allowed = requesting.purchasingMode();
		if (allowed != PurchasingMode.MIXED && allowed != mode) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "purchase-mode-not-allowed",
					"This company's purchasing mode does not allow that choice.");
		}
		long charged = request.chargedCompanyId();
		long buying = switch (mode) {
			case CENTRALIZED -> organization.purchasingCenterOf(request.groupId()).map(OrganizationDirectory.CompanyRef::id)
				.orElse(request.requestingCompanyId());
			case DIRECT, ON_BEHALF, MIXED -> request.requestingCompanyId();
		};
		if (charged != request.chargedCompanyId()) {
			throw ApiProblemException.conflict("invalid-state", "Cost allocation cannot change with the purchase mode.");
		}
		String from = request.purchaseMode();
		requests.confirmPurchaseMode(request.id(), mode, buying, user.id(), now);
		audit(user, request, "PURCHASE_MODE_SET", request.status(), null,
				"{\"from\":" + json(from) + ",\"to\":\"" + mode.name() + "\",\"buyingCompanyId\":" + buying
						+ ",\"chargedCompanyId\":" + charged + "}");
	}

	private QueueItem item(RequestSnapshot r) {
		return new QueueItem(r.id(), r.version(), r.number(), r.title(), r.status(), r.userStatus().name(),
				refUser(r.requesterUserId()), company(r.buyingCompanyId()), company(r.requestingCompanyId()),
				r.totalAmount(), r.currency(), null, r.priority(), r.purchaseType(), r.purchaseMode(),
				r.buyingCompanyProposed(), r.assignedBuyerUserId() == null ? null : refUser(r.assignedBuyerUserId()),
				r.requiredDate(), r.submittedAt(), gaps(r));
	}

	private static List<String> gaps(RequestSnapshot r) {
		List<String> gaps = new ArrayList<>();
		if (!StringUtils.hasText(r.title())) {
			gaps.add("title");
		}
		if (r.categoryId() == null) {
			gaps.add("category");
		}
		if (r.requiredDate() == null) {
			gaps.add("requiredDate");
		}
		if (r.lines() == null || r.lines().isEmpty()) {
			gaps.add("lines");
		}
		return gaps;
	}

	private Ref company(long id) {
		return organization.findCompany(id)
			.map(c -> new Ref(c.id(), c.code(), c.name()))
			.orElse(new Ref(id, "", ""));
	}

	private Ref refUser(long id) {
		return users.findContact(id)
			.map(u -> new Ref(u.id(), "", u.displayName()))
			.orElse(new Ref(id, "", ""));
	}

	private static String name(Map<Long, UserDirectory.UserContact> people, long id) {
		UserDirectory.UserContact c = people.get(id);
		return c == null ? "" : c.displayName();
	}

	private void requirePurchasing(CurrentUser user) {
		if (!user.hasRole(Role.BUYER) && !user.hasRole(Role.PURCHASING_MANAGER)) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requirePurchasingOn(CurrentUser user, RequestSnapshot request) {
		if (!user.hasRoleIn(Role.BUYER, request.buyingCompanyId())
				&& !user.hasRoleIn(Role.PURCHASING_MANAGER, request.buyingCompanyId())) {
			throw ApiProblemException.forbidden();
		}
	}

	private void requireManagerOf(CurrentUser user, long buyingCompanyId) {
		if (!user.hasRoleIn(Role.PURCHASING_MANAGER, buyingCompanyId)) {
			throw ApiProblemException.forbidden();
		}
	}

	private static void requireVersion(Integer version, RequestSnapshot request) {
		if (version == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("version", "required")));
		}
		if (version != request.version()) {
			throw new ApiProblemException(HttpStatus.CONFLICT, "stale-version",
					"The request was changed by someone else. Reload it and try again.");
		}
	}

	private void audit(CurrentUser user, RequestSnapshot request, String action, String toStatus, String comment,
			String payload) {
		auditLog.record(new AuditRecord(user.id(), user.displayName(), request.buyingCompanyId(), ENTITY,
				Long.toString(request.id()), action, request.status(), toStatus, comment, payload));
	}

	private void notify(RequestSnapshot request, long recipient, String kind, CurrentUser actor) {
		notifications.enqueue(new Notice(kind, recipient, request.id(),
				Map.of("number", nullToEmpty(request.number()), "title", nullToEmpty(request.title()), "actor",
						actor.displayName()),
				List.of(), List.of(new Notice.Action("mail.action.open", "/requests/" + request.id())),
				"/requests/" + request.id()));
	}

	private void notifyAuthors(RequestSnapshot request, String kind, CurrentUser actor) {
		notify(request, request.requesterUserId(), kind, actor);
		if (request.enteredByUserId() != request.requesterUserId()) {
			notify(request, request.enteredByUserId(), kind, actor);
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
