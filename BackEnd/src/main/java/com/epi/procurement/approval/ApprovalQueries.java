package com.epi.procurement.approval;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

/**
 * Read side of approvals: inbox, counts, the tracker data of a request and the approver preview.
 */
@Service
class ApprovalQueries {

	private static final Set<String> SORTS = Set.of("dueAt", "waitingSince", "amount", "number", "requiredDate",
			"createdAt");

	private final ApprovalStore store;

	private final ApprovalEngine engine;

	private final ApproverResolver resolver;

	private final RequestWorkflow requests;

	private final UserDirectory users;

	private final OrganizationDirectory organization;

	private final ExchangeRates rates;

	private final Clock clock;

	ApprovalQueries(ApprovalStore store, ApprovalEngine engine, ApproverResolver resolver, RequestWorkflow requests,
			UserDirectory users, OrganizationDirectory organization, ExchangeRates rates, ObjectProvider<Clock> clock) {
		this.store = store;
		this.engine = engine;
		this.resolver = resolver;
		this.requests = requests;
		this.users = users;
		this.organization = organization;
		this.rates = rates;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * @param status {@code PENDING} (default) or {@code DECIDED} (my last 200 decisions)
	 * @param sort {@code field,asc|desc} with field waitingSince (default desc = newest first), dueAt, amount,
	 * number, requiredDate, createdAt
	 */
	@Transactional(readOnly = true)
	ApprovalViews.InboxPage inbox(CurrentUser user, String status, String q, String sort, int page, int size) {
		boolean decided = "DECIDED".equals(status);
		if (status != null && !decided && !"PENDING".equals(status)) {
			throw ApiProblemException.invalidParameter("status");
		}
		if (page < 0) {
			throw ApiProblemException.invalidParameter("page");
		}
		if (size < 1 || size > 100) {
			throw ApiProblemException.invalidParameter("size");
		}
		Comparator<ApprovalViews.InboxItem> comparator = comparator(sort);
		List<ApprovalStore.Task> tasks = decided ? store.decidedTasksOfUser(user.id(), 200)
				: store.pendingTasksOfUser(user.id());
		Map<Long, RequestSnapshot> snapshots = requests.findAll(tasks.stream().map(ApprovalStore.Task::requestId).collect(Collectors.toSet()));
		Map<Long, ApprovalStore.Round> rounds = new HashMap<>();
		Set<Long> people = new HashSet<>();
		snapshots.values().forEach(r -> people.add(r.requesterUserId()));
		tasks.forEach(t -> {
			if (t.originalUserId() != null) {
				people.add(t.originalUserId());
			}
		});
		Map<Long, UserDirectory.UserContact> contacts = users.findContacts(people);
		Instant now = clock.instant();
		String text = StringUtils.hasText(q) ? q.strip().toLowerCase(Locale.ROOT) : null;
		List<ApprovalViews.InboxItem> items = new ArrayList<>();
		for (ApprovalStore.Task task : tasks) {
			RequestSnapshot request = snapshots.get(task.requestId());
			if (request == null || request.groupId() != user.groupId()) {
				continue;
			}
			if (text != null && !(contains(request.number(), text) || contains(request.title(), text))) {
				continue;
			}
			ApprovalStore.Round round = rounds.computeIfAbsent(task.roundId(),
					id -> store.roundsOfRequest(task.requestId()).stream().filter(r -> r.id() == id).findFirst().orElse(null));
			items.add(new ApprovalViews.InboxItem(task.id(), task.version(), task.status(), request.id(), request.number(),
					request.title(), request.userStatus().name(), ref(contacts, request.requesterUserId()),
					company(request.requestingCompanyId()), money(request.totalAmount(), request.currency()),
					request.currency(), round == null ? null : round.companyAmount(),
					round == null ? null : round.companyCurrency(), task.stageNo(), task.stageName(), task.policy(),
					task.createdAt(), task.dueAt(), task.isPending() && !task.questionOpen() && !task.dueAt().isAfter(now),
					task.questionOpen(), task.originalUserId() == null ? null : ref(contacts, task.originalUserId()),
					task.decidedAt(), request.requiredDate()));
		}
		items.sort(comparator);
		int from = Math.min(page * size, items.size());
		int to = Math.min(from + size, items.size());
		return new ApprovalViews.InboxPage(items.subList(from, to), page, size, items.size(),
				(items.size() + size - 1) / size);
	}

	@Transactional(readOnly = true)
	ApprovalViews.InboxCount count(CurrentUser user) {
		long[] counts = store.countPending(user.id(), clock.instant());
		return new ApprovalViews.InboxCount(counts[0], counts[1]);
	}

	/**
	 * Tracker data (S2-AC-11): rounds, stages, tasks, decision history and the caller's actions. 404 when the
	 * request is not visible to the caller.
	 */
	@Transactional(readOnly = true)
	ApprovalViews.RequestApproval requestApproval(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		List<ApprovalStore.Round> rounds = store.roundsOfRequest(requestId);
		List<ApprovalStore.Task> tasks = store.tasksOfRequest(requestId);
		List<ApprovalStore.Event> events = store.eventsOfRequest(requestId);
		Set<Long> people = new HashSet<>();
		tasks.forEach(t -> {
			people.add(t.assigneeUserId());
			if (t.originalUserId() != null) {
				people.add(t.originalUserId());
			}
			if (t.decidedBy() != null) {
				people.add(t.decidedBy());
			}
		});
		events.forEach(e -> {
			addIfPresent(people, e.actorUserId());
			addIfPresent(people, e.onBehalfOfUserId());
			addIfPresent(people, e.targetUserId());
		});
		Map<Long, UserDirectory.UserContact> contacts = users.findContacts(people);
		Instant now = clock.instant();
		Map<Long, Integer> roundNumbers = rounds.stream().collect(Collectors.toMap(ApprovalStore.Round::id, ApprovalStore.Round::roundNo));

		List<ApprovalViews.RoundView> roundViews = new ArrayList<>();
		for (ApprovalStore.Round round : rounds) {
			ApprovalStore.Rule rule = store.rule(round.ruleId()).orElse(null);
			List<ApprovalStore.Stage> stages = rule == null ? List.of() : rule.applicableStages(round.companyAmount());
			List<ApprovalViews.StageView> stageViews = new ArrayList<>();
			for (ApprovalStore.Stage stage : stages) {
				List<ApprovalViews.TaskView> taskViews = tasks.stream()
					.filter(t -> t.roundId() == round.id() && t.stageNo() == stage.stageNo())
					.map(t -> taskView(t, contacts, now))
					.toList();
				stageViews.add(new ApprovalViews.StageView(stage.stageNo(), stage.name(), stage.policy(),
						stage.approverType(), stage.slaHours(), stageStatus(round, stage, taskViews), taskViews));
			}
			roundViews.add(new ApprovalViews.RoundView(round.id(), round.roundNo(), round.status(),
					rule == null ? "" : rule.name(), rule == null ? 0 : rule.versionNo(),
					new ApprovalViews.Amounts(round.requestAmount(), round.requestCurrency(), round.companyAmount(),
							round.companyCurrency(), round.rate(), round.rateDate()),
					round.currentStageNo(), round.startedAt(), round.completedAt(), stageViews));
		}

		List<ApprovalViews.EventView> eventViews = events.stream()
			.map(e -> new ApprovalViews.EventView(e.id(), roundNumbers.getOrDefault(e.roundId(), 0), e.taskId(), e.kind(),
					refOrNull(contacts, e.actorUserId()), refOrNull(contacts, e.onBehalfOfUserId()),
					refOrNull(contacts, e.targetUserId()), e.text(), e.fromStatus(), e.toStatus(), e.channel(),
					e.occurredAt()))
			.toList();

		ApprovalStore.Round active = rounds.stream().filter(r -> "ACTIVE".equals(r.status())).findFirst().orElse(null);
		List<ApprovalStore.Task> pending = active == null ? List.of()
				: tasks.stream().filter(t -> t.roundId() == active.id() && t.isPending()).toList();
		List<ApprovalViews.UserRef> nextActors = pending.stream().map(t -> ref(contacts, t.assigneeUserId())).toList();
		Instant waitingSince = pending.stream().map(ApprovalStore.Task::createdAt).min(Comparator.naturalOrder()).orElse(null);

		List<String> actions = new ArrayList<>();
		ApprovalViews.MyTask myTask = null;
		ApprovalStore.Task mine = pending.stream().filter(t -> t.assigneeUserId() == user.id()).findFirst().orElse(null);
		if (mine != null) {
			actions.addAll(List.of("APPROVE", "REJECT", "RETURN"));
			if (!mine.questionOpen()) {
				actions.add("QUESTION");
			}
			actions.add("DELEGATE");
			myTask = new ApprovalViews.MyTask(mine.id(), mine.version(), mine.stageNo(), mine.stageName(),
					mine.questionOpen());
		}
		List<ApprovalViews.OpenQuestion> openQuestions = new ArrayList<>();
		for (ApprovalStore.Task task : pending) {
			if (task.questionOpen()) {
				events.stream()
					.filter(e -> Objects.equals(e.taskId(), task.id()) && "QUESTION".equals(e.kind()))
					.reduce((first, second) -> second)
					.ifPresent(e -> openQuestions.add(new ApprovalViews.OpenQuestion(task.id(),
							refOrNull(contacts, e.actorUserId()), e.text(), e.occurredAt())));
			}
		}
		if (!openQuestions.isEmpty() && request.isAuthor(user.id())) {
			actions.add("ANSWER");
		}
		return new ApprovalViews.RequestApproval(requestId, request.status(), request.userStatus().name(),
				active == null ? (rounds.isEmpty() ? null : rounds.get(rounds.size() - 1).roundNo()) : active.roundNo(),
				nextActors, waitingSince, roundViews, eventViews, actions, myTask, openQuestions);
	}

	/**
	 * Who would approve the request if it were submitted now (S2DR-17). Problems are reported, not thrown.
	 */
	@Transactional(readOnly = true)
	ApprovalViews.Preview preview(CurrentUser user, long requestId) {
		RequestSnapshot request = requests.requireVisible(user, requestId);
		OrganizationDirectory.CompanyRef company = organization.findCompany(request.requestingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		Instant now = clock.instant();
		ApprovalEngine.Basis basis;
		try {
			basis = engine.basis(request, company, LocalDate.ofInstant(now, ZoneId.of(company.timeZone())));
		}
		catch (ApiProblemException ex) {
			return new ApprovalViews.Preview(ex.getCode(), null, null, List.of());
		}
		Map<Long, String> names = new LinkedHashMap<>();
		List<ApprovalViews.PreviewStage> stages = new ArrayList<>();
		String problem = null;
		for (ApprovalStore.Stage stage : basis.rule().applicableStages(basis.amount())) {
			List<ApprovalViews.PreviewApprover> approvers = new ArrayList<>();
			for (long approver : resolver.resolveStage(stage, request)) {
				ApproverResolver.Routing routing = resolver.route(approver, stage.approverType(), stage.approverUserId(),
						request, now);
				approvers.add(new ApprovalViews.PreviewApprover(named(names, routing.assignee()),
						routing.original() == null ? null : named(names, routing.original()), routing.note()));
			}
			if (approvers.isEmpty()) {
				problem = "no-eligible-approver";
			}
			stages.add(new ApprovalViews.PreviewStage(stage.stageNo(), stage.name(), stage.policy(), stage.approverType(),
					approvers));
		}
		return new ApprovalViews.Preview(problem, basis.rule().name(),
				new ApprovalViews.Amounts(request.totalAmount(), request.currency(), basis.amount(),
						company.currencyCode(), basis.rate(), basis.rateDate()),
				stages);
	}

	/**
	 * Users the task holder may delegate the task to (S2-AC-05). Only the holder may ask.
	 */
	@Transactional(readOnly = true)
	List<ApprovalViews.Delegate> delegates(CurrentUser user, long taskId) {
		ApprovalStore.Task task = store.task(taskId).orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.requireVisible(user, task.requestId());
		if (task.assigneeUserId() != user.id()) {
			throw ApiProblemException.forbidden();
		}
		if (!task.isPending()) {
			throw ApiProblemException.conflict("task-not-pending", "This approval task has already been decided.");
		}
		Set<Long> exclude = new HashSet<>();
		exclude.add(user.id());
		store.tasksOfRound(task.roundId()).stream().filter(ApprovalStore.Task::isPending).forEach(t -> exclude.add(t.assigneeUserId()));
		return resolver.eligibleDelegates(task, request, exclude)
			.stream()
			.map(h -> new ApprovalViews.Delegate(h.userId(), h.displayName()))
			.toList();
	}

	/**
	 * Sprint 1 requests still waiting for an approval circuit (S2DR-01), for administrators.
	 */
	@Transactional(readOnly = true)
	List<ApprovalViews.AwaitingCircuit> awaitingCircuit(CurrentUser admin) {
		Absences.requireAdmin(admin);
		return requests.findAll(requests.legacySubmittedIds())
			.values()
			.stream()
			.filter(r -> r.groupId() == admin.groupId())
			.sorted(Comparator.comparing(RequestSnapshot::id))
			.map(r -> new ApprovalViews.AwaitingCircuit(r.id(), r.number(), r.title(), company(r.requestingCompanyId()),
					r.submittedAt()))
			.toList();
	}

	private ApprovalViews.UserRef named(Map<Long, String> cache, long id) {
		String name = cache.computeIfAbsent(id,
				key -> users.findContact(key).map(UserDirectory.UserContact::displayName).orElse(""));
		return new ApprovalViews.UserRef(id, name);
	}

	private static String stageStatus(ApprovalStore.Round round, ApprovalStore.Stage stage,
			List<ApprovalViews.TaskView> tasks) {
		if (tasks.isEmpty()) {
			return "ACTIVE".equals(round.status()) ? "WAITING" : "SKIPPED";
		}
		if (tasks.stream().anyMatch(t -> "PENDING".equals(t.status()))) {
			return "IN_PROGRESS";
		}
		if (tasks.stream().anyMatch(t -> "REJECTED".equals(t.status()))) {
			return "REJECTED";
		}
		if (tasks.stream().anyMatch(t -> "RETURNED".equals(t.status()))) {
			return "RETURNED";
		}
		if (tasks.stream().anyMatch(t -> "APPROVED".equals(t.status()))) {
			return "APPROVED";
		}
		return "CANCELLED";
	}

	private ApprovalViews.TaskView taskView(ApprovalStore.Task t, Map<Long, UserDirectory.UserContact> contacts,
			Instant now) {
		return new ApprovalViews.TaskView(t.id(), t.version(), ref(contacts, t.assigneeUserId()),
				refOrNull(contacts, t.originalUserId()), t.routingNote(), t.status(), t.createdAt(), t.dueAt(),
				t.escalationAt(), t.isPending() && !t.questionOpen() && !t.dueAt().isAfter(now), t.questionOpen(),
				t.decidedAt(), refOrNull(contacts, t.decidedBy()), t.comment());
	}

	private ApprovalViews.Ref company(long id) {
		return organization.findCompany(id)
			.map(c -> new ApprovalViews.Ref(c.id(), c.code(), c.name()))
			.orElse(new ApprovalViews.Ref(id, "", ""));
	}

	private BigDecimal money(BigDecimal amount, String currency) {
		return amount == null ? null : amount.setScale(rates.minorUnits(currency), RoundingMode.HALF_UP);
	}

	private static Comparator<ApprovalViews.InboxItem> comparator(String sort) {
		String field = "waitingSince";
		boolean descending = true;
		if (StringUtils.hasText(sort)) {
			String[] parts = sort.split(",");
			field = parts[0].trim();
			if (!SORTS.contains(field) || parts.length > 2) {
				throw ApiProblemException.invalidParameter("sort");
			}
			if ("createdAt".equals(field)) {
				field = "waitingSince";
			}
			if (parts.length == 2) {
				String direction = parts[1].trim().toLowerCase(Locale.ROOT);
				if (!direction.equals("asc") && !direction.equals("desc")) {
					throw ApiProblemException.invalidParameter("sort");
				}
				descending = direction.equals("desc");
			}
		}
		Function<ApprovalViews.InboxItem, Comparable<?>> key = switch (field) {
			case "waitingSince" -> ApprovalViews.InboxItem::waitingSince;
			case "amount" -> i -> i.companyAmount() == null ? BigDecimal.ZERO : i.companyAmount();
			case "number" -> i -> i.number() == null ? "" : i.number();
			case "requiredDate" -> i -> i.requiredDate() == null ? LocalDate.MAX : i.requiredDate();
			default -> ApprovalViews.InboxItem::dueAt;
		};
		@SuppressWarnings({ "unchecked", "rawtypes" })
		Comparator<ApprovalViews.InboxItem> base = Comparator.comparing((Function) key,
				Comparator.nullsLast(Comparator.naturalOrder()));
		base = descending ? base.reversed() : base;
		return base.thenComparing(ApprovalViews.InboxItem::taskId, Comparator.reverseOrder());
	}

	private static boolean contains(String value, String text) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(text);
	}

	private static void addIfPresent(Set<Long> set, Long value) {
		if (value != null) {
			set.add(value);
		}
	}

	private static ApprovalViews.UserRef ref(Map<Long, UserDirectory.UserContact> contacts, long id) {
		UserDirectory.UserContact contact = contacts.get(id);
		return new ApprovalViews.UserRef(id, contact == null ? "" : contact.displayName());
	}

	private static ApprovalViews.UserRef refOrNull(Map<Long, UserDirectory.UserContact> contacts, Long id) {
		return id == null ? null : ref(contacts, id);
	}

}
