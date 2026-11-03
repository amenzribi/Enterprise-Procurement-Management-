package com.epi.procurement.approval;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.organization.ExchangeRates;
import com.epi.procurement.organization.OrganizationDirectory;
import com.epi.procurement.organization.WorkingCalendar;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.platform.web.FieldViolation;
import com.epi.procurement.platform.web.ValidationFailedException;
import com.epi.procurement.request.RequestAdministrativelyCancelledEvent;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestSubmittedEvent;
import com.epi.procurement.request.RequestWorkflow;

/**
 * Approval circuits (S2-AC-02..07). Every state change follows one lock order - request row, approval round, round
 * tasks, audit chain head (S2DR-08) - so inbox decisions, one-tap approvals and submissions cannot deadlock each
 * other, and exactly one of two concurrent decisions on a task wins. Each action writes a decision event, the
 * notifications and an audit event in the same transaction.
 */
@Service
class ApprovalEngine {

	static final String ENTITY = "PURCHASE_REQUEST";

	private final ApprovalStore store;

	private final ApproverResolver resolver;

	private final RequestWorkflow requests;

	private final OrganizationDirectory organization;

	private final ExchangeRates rates;

	private final ApprovalNotices notices;

	private final AuditLog auditLog;

	private final Clock clock;

	private final int slaUnitMinutes;

	ApprovalEngine(ApprovalStore store, ApproverResolver resolver, RequestWorkflow requests,
			OrganizationDirectory organization, ExchangeRates rates, ApprovalNotices notices, AuditLog auditLog,
			ObjectProvider<Clock> clock, @Value("${app.approval.sla-unit-minutes:60}") int slaUnitMinutes) {
		this.store = store;
		this.resolver = resolver;
		this.requests = requests;
		this.organization = organization;
		this.rates = rates;
		this.notices = notices;
		this.auditLog = auditLog;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
		this.slaUnitMinutes = slaUnitMinutes;
	}

	// Circuit start ---------------------------------------------------------------------------------------------

	/**
	 * Runs inside the submission transaction (synchronous listener): a failure rolls the submission back.
	 */
	@EventListener
	void onSubmitted(RequestSubmittedEvent event) {
		start(event.requestId(), event.roundNo(), event.submittedAt(), null, true);
	}

	/**
	 * GLOBAL_ADMIN cancel/return: cancel pending tasks, close active rounds, invalidate unused one-tap links.
	 * Completed decisions stay intact.
	 */
	@EventListener
	void onAdministrativelyCancelled(RequestAdministrativelyCancelledEvent event) {
		Optional<ApprovalStore.Round> round = store.activeRound(event.requestId());
		store.cancelAllPendingForRequest(event.requestId(), event.occurredAt());
		round.ifPresent(r -> store.insertEvent(event.requestId(), r.id(), null, "ADMIN_" + event.outcome(),
				event.actorUserId(), null, null, "Administrative " + event.outcome(), "IN_APPROVAL", event.outcome(),
				"APP", event.occurredAt()));
	}

	/**
	 * Starts round {@code roundNo}: converts the amount to company currency, chooses the rule, checks every applicable
	 * stage has an approver, records the round and creates the first stage's tasks.
	 *
	 * @param basisDate date of the exchange rate (the submission date in the company's time zone when null)
	 */
	void start(long requestId, int roundNo, Instant now, LocalDate basisDate, boolean notifyAdmins) {
		RequestSnapshot request = requests.lockForApproval(requestId);
		OrganizationDirectory.CompanyRef company = organization.findCompany(request.requestingCompanyId())
			.orElseThrow(ApiProblemException::notFound);
		LocalDate date = basisDate != null ? basisDate : LocalDate.ofInstant(now, ZoneId.of(company.timeZone()));
		Basis basis = basis(request, company, date);
		List<ApprovalStore.Stage> stages = basis.rule().applicableStages(basis.amount());
		for (ApprovalStore.Stage stage : stages) {
			if (resolver.resolveStage(stage, request).isEmpty()) {
				if (notifyAdmins) {
					notices.noEligibleApprover(request, stage.name());
				}
				throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "no-eligible-approver",
						"An approval stage has no eligible approver.", Map.of("stage", stage.name()));
			}
		}
		long roundId = store.insertRound(requestId, company.id(), roundNo, basis.rule().id(), request.totalAmount(),
				request.currency(), basis.amount(), company.currencyCode(), basis.rate(), basis.rateDate(),
				stages.get(0).stageNo(), now);
		requests.recordApprovalBasis(requestId, basis.amount(), company.currencyCode(), basis.rate(), basis.rateDate(),
				basis.rule().id());
		createStageTasks(roundId, stages.get(0), request, now, null);
	}

	/**
	 * Converts and matches without side effects; throws the 422 a submission would get.
	 */
	Basis basis(RequestSnapshot request, OrganizationDirectory.CompanyRef company, LocalDate date) {
		BigDecimal total = request.totalAmount() == null ? BigDecimal.ZERO : request.totalAmount();
		ExchangeRates.Conversion conversion = rates
			.convert(request.groupId(), total, request.currency(), company.currencyCode(), date)
			.orElseThrow(() -> new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "missing-exchange-rate",
					"No exchange rate to convert the request currency to the company currency.",
					Map.of("currency", request.currency(), "companyCurrency", company.currencyCode())));
		List<ApprovalStore.Rule> candidates = store.activeRulesOfCompany(company.id())
			.stream()
			.filter(r -> r.matches(request.categoryId(), request.purchaseType(), conversion.amount()))
			.toList();
		if (candidates.isEmpty()) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "no-approval-rule",
					"No active approval rule applies to this request.");
		}
		int best = candidates.stream().mapToInt(ApprovalStore.Rule::specificity).max().orElse(0);
		List<ApprovalStore.Rule> winners = candidates.stream().filter(r -> r.specificity() == best).toList();
		if (winners.size() > 1) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "ambiguous-approval-rule",
					"Several approval rules apply equally to this request.");
		}
		ApprovalStore.Rule rule = winners.get(0);
		if (rule.applicableStages(conversion.amount()).isEmpty()) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "no-approval-rule",
					"The approval rule has no stage for this amount.");
		}
		return new Basis(rule, conversion.amount(), conversion.rate(), conversion.rateDate());
	}

	private void createStageTasks(long roundId, ApprovalStore.Stage stage, RequestSnapshot request, Instant now,
			Long actorId) {
		List<Long> approvers = resolver.resolveStage(stage, request);
		if (approvers.isEmpty()) {
			notices.noEligibleApprover(request, stage.name());
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "no-eligible-approver",
					"An approval stage has no eligible approver.", Map.of("stage", stage.name()));
		}
		WorkingCalendar calendar = organization.workingCalendar(request.requestingCompanyId());
		int slaMinutes = stage.slaHours() * slaUnitMinutes;
		Instant due = calendar.addWorkingMinutes(now, slaMinutes);
		Instant escalation = calendar.addWorkingMinutes(due, slaMinutes);
		Set<Long> assignees = new LinkedHashSet<>();
		for (PlannedTask planned : planTasks(stage, approvers, request, now)) {
			assignees.add(planned.assignee());
			Long original = planned.absentApprovers().isEmpty() ? null : planned.absentApprovers().get(0);
			ApprovalStore.NewTask task = new ApprovalStore.NewTask(roundId, request.id(), request.requestingCompanyId(),
					stage.stageNo(), stage.name(), stage.approverType(), stage.policy(), planned.assignee(), original,
					planned.note(), slaMinutes, stage.escalationRole(), due, escalation, now);
			long taskId = store.insertTask(task);
			requests.addParticipant(request.id(), planned.assignee(), "APPROVER", now);
			store.insertEvent(request.id(), roundId, taskId, "TASK_CREATED", actorId, original, planned.assignee(), null,
					null, null, "APP", now);
			// Every absent approver whose task went to this person is visible in the tracker and the audit trail.
			for (long absent : planned.absentApprovers()) {
				store.insertEvent(request.id(), roundId, taskId, "ABSENCE_ROUTED", actorId, absent, planned.assignee(),
						null, null, null, "APP", now);
				auditLog.record(new AuditRecord(actorId, actorId == null ? "system" : notices.name(actorId),
						request.requestingCompanyId(), ENTITY, Long.toString(request.id()), "APPROVAL_ABSENCE_ROUTED",
						null, null, null, "{\"taskId\":" + taskId + ",\"stage\":" + stage.stageNo() + ",\"absent\":"
								+ absent + ",\"assignee\":" + planned.assignee() + "}"));
			}
			notices.task("TASK_ASSIGNED", stage.name(), taskId, planned.assignee(), request, actorId, now);
		}
		if (assignees.isEmpty()) {
			throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "no-eligible-approver",
					"An approval stage has no eligible approver.", Map.of("stage", stage.name()));
		}
		requests.assignOwner(request.id(), assignees.iterator().next(), now);
		store.updateRound(roundId, "ACTIVE", stage.stageNo(), null);
	}

	/**
	 * Routes every approver of the stage before creating any task (S2CR-15), independently of the approver order:
	 * direct approvers first, then absent approvers' delegates sorted by absent approver.
	 * <ul>
	 * <li>ANY: tasks that land on the same person collapse into one task, marked ABSENCE_DELEGATE on behalf of the
	 * absent approver(s); one ABSENCE_ROUTED event per absent approver.</li>
	 * <li>ALL: one person never holds two required approvals. A delegate who already holds a task of the stage does
	 * not take another one: the absent approver keeps it, flagged ABSENCE_UNRESOLVED.</li>
	 * </ul>
	 */
	private List<PlannedTask> planTasks(ApprovalStore.Stage stage, List<Long> approvers, RequestSnapshot request,
			Instant now) {
		List<ApproverResolver.Routing> direct = new ArrayList<>();
		List<ApproverResolver.Routing> delegated = new ArrayList<>();
		for (long approver : approvers) {
			ApproverResolver.Routing routing = resolver.route(approver, stage.approverType(), stage.approverUserId(),
					request, now);
			if (request.isAuthor(routing.assignee())) {
				routing = new ApproverResolver.Routing(approver, null, "ABSENCE_UNRESOLVED");
			}
			(routing.original() == null ? direct : delegated).add(routing);
		}
		delegated.sort(Comparator.comparingLong(ApproverResolver.Routing::original));
		Map<Long, PlannedTask> plan = new java.util.LinkedHashMap<>();
		for (ApproverResolver.Routing routing : direct) {
			plan.putIfAbsent(routing.assignee(), new PlannedTask(routing.assignee(), routing.note(), new ArrayList<>()));
		}
		boolean all = "ALL".equals(stage.policy());
		for (ApproverResolver.Routing routing : delegated) {
			PlannedTask existing = plan.get(routing.assignee());
			if (existing == null) {
				List<Long> absent = new ArrayList<>(List.of(routing.original()));
				plan.put(routing.assignee(), new PlannedTask(routing.assignee(), "ABSENCE_DELEGATE", absent));
			}
			else if (all) {
				plan.putIfAbsent(routing.original(),
						new PlannedTask(routing.original(), "ABSENCE_UNRESOLVED", new ArrayList<>()));
			}
			else {
				existing.absentApprovers().add(routing.original());
				plan.put(routing.assignee(),
						new PlannedTask(routing.assignee(), "ABSENCE_DELEGATE", existing.absentApprovers()));
			}
		}
		return List.copyOf(plan.values());
	}

	/**
	 * @param absentApprovers designated approvers whose task went to {@code assignee} (the first is "on behalf of")
	 */
	private record PlannedTask(long assignee, String note, List<Long> absentApprovers) {
	}

	// Decisions -------------------------------------------------------------------------------------------------

	enum Action {
		APPROVE, REJECT, RETURN, QUESTION, DELEGATE
	}

	/**
	 * Applies an approver's decision. The actor must be the task's assignee: a task the actor cannot see is 404, a
	 * task of a visible request held by someone else is 403.
	 *
	 * @param channel {@code APP} or {@code EMAIL} (one-tap link)
	 */
	@Transactional
	long decide(CurrentUser actor, long taskId, Integer version, Action action, String text, Long delegateTo,
			String channel) {
		ApprovalStore.Task snapshot = store.task(taskId).orElseThrow(ApiProblemException::notFound);
		if (snapshot.assigneeUserId() != actor.id()) {
			requests.requireVisible(actor, snapshot.requestId());
			throw ApiProblemException.forbidden();
		}
		if (version == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("version", "required")));
		}
		String clean = StringUtils.hasText(text) ? text.strip() : null;
		if ((action == Action.REJECT || action == Action.RETURN) && clean == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("reason", "reason-required")));
		}
		if (action == Action.QUESTION && clean == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("question", "required")));
		}
		if (clean != null && clean.length() > 2000) {
			throw new ValidationFailedException(List.of(FieldViolation.of(action == Action.QUESTION ? "question" : "reason",
					"too-long", "max", 2000)));
		}

		RequestSnapshot request = requests.lockForApproval(snapshot.requestId());
		ApprovalStore.Round round = store.lockRound(snapshot.roundId());
		List<ApprovalStore.Task> tasks = store.lockTasksOfRound(round.id());
		ApprovalStore.Task task = tasks.stream().filter(t -> t.id() == taskId).findFirst()
			.orElseThrow(ApiProblemException::notFound);
		if (!task.isPending() || !"ACTIVE".equals(round.status()) || task.assigneeUserId() != actor.id()) {
			throw ApiProblemException.conflict("task-not-pending", "This approval task has already been decided.");
		}
		if (version != task.version()) {
			throw ApiProblemException.conflict("stale-version", "The task was changed by someone else. Reload it.");
		}
		Instant now = clock.instant();
		switch (action) {
			case APPROVE -> approve(actor, request, round, tasks, task, clean, channel, now);
			case REJECT -> end(actor, request, round, task, "REJECTED", clean, channel, now);
			case RETURN -> end(actor, request, round, task, "RETURNED", clean, channel, now);
			case QUESTION -> question(actor, request, round, task, clean, now);
			case DELEGATE -> delegate(actor, request, round, tasks, task, delegateTo, clean, now);
		}
		return request.id();
	}

	private void approve(CurrentUser actor, RequestSnapshot request, ApprovalStore.Round round,
			List<ApprovalStore.Task> tasks, ApprovalStore.Task task, String comment, String channel, Instant now) {
		supersedeQuestion(actor, request, round, task, now);
		store.decideTask(task.id(), "APPROVED", actor.id(), now, comment);
		store.insertEvent(request.id(), round.id(), task.id(), "APPROVED", actor.id(), task.originalUserId(), null,
				comment, null, null, channel, now);
		List<ApprovalStore.Task> otherPending = tasks.stream()
			.filter(t -> t.isPending() && t.id() != task.id() && t.stageNo() == task.stageNo())
			.toList();
		boolean stageComplete = "ANY".equals(task.policy()) || otherPending.isEmpty();
		String toStatus = "IN_APPROVAL";
		if (stageComplete) {
			if (!otherPending.isEmpty()) {
				store.cancelPendingTasks(round.id(), task.id(), "CANCELLED", now);
			}
			ApprovalStore.Rule rule = store.rule(round.ruleId()).orElseThrow();
			Optional<ApprovalStore.Stage> next = rule.applicableStages(round.companyAmount())
				.stream()
				.filter(s -> s.stageNo() > task.stageNo())
				.min(Comparator.comparingInt(ApprovalStore.Stage::stageNo));
			if (next.isPresent()) {
				createStageTasks(round.id(), next.get(), request, now, actor.id());
			}
			else {
				store.updateRound(round.id(), "APPROVED", task.stageNo(), now);
				requests.markApproved(request.id(), actor.id(), now);
				store.insertEvent(request.id(), round.id(), null, "ROUND_APPROVED", actor.id(), null, null, null,
						"IN_APPROVAL", "APPROVED", channel, now);
				notices.authors("REQUEST_APPROVED", request, actor.id(), task.stageName(), null);
				toStatus = "APPROVED";
			}
		}
		else {
			requests.assignOwner(request.id(), otherPending.get(0).assigneeUserId(), now);
		}
		audit(actor, request, "APPROVAL_APPROVED", toStatus, comment, task, channel);
	}

	/**
	 * Reject (terminal) or return for revision: ends the round, cancels the other pending tasks (and with them their
	 * one-tap links).
	 */
	private void end(CurrentUser actor, RequestSnapshot request, ApprovalStore.Round round, ApprovalStore.Task task,
			String outcome, String reason, String channel, Instant now) {
		if (task.questionOpen()) {
			// Recorded for the history (S2CR-11); the outcome notice already tells the authors.
			store.insertEvent(request.id(), round.id(), task.id(), "QUESTION_SUPERSEDED", actor.id(), null, null, null,
					null, null, channel, now);
		}
		store.decideTask(task.id(), outcome, actor.id(), now, reason);
		store.cancelPendingTasks(round.id(), task.id(), "CANCELLED", now);
		store.updateRound(round.id(), outcome, task.stageNo(), now);
		boolean rejected = "REJECTED".equals(outcome);
		String toStatus = rejected ? "REJECTED" : "REVISION_REQUESTED";
		if (rejected) {
			requests.markRejected(request.id(), actor.id(), now, reason);
		}
		else {
			requests.markReturned(request.id(), actor.id(), now, reason);
		}
		store.insertEvent(request.id(), round.id(), task.id(), outcome, actor.id(), task.originalUserId(), null, reason,
				"IN_APPROVAL", toStatus, channel, now);
		notices.authors(rejected ? "REQUEST_REJECTED" : "REQUEST_RETURNED", request, actor.id(), task.stageName(),
				reason);
		audit(actor, request, rejected ? "APPROVAL_REJECTED" : "APPROVAL_RETURNED", toStatus, reason, task, channel);
	}

	private void question(CurrentUser actor, RequestSnapshot request, ApprovalStore.Round round,
			ApprovalStore.Task task, String question, Instant now) {
		if (task.questionOpen()) {
			throw ApiProblemException.conflict("question-already-open", "A question is already waiting for an answer.");
		}
		store.openQuestion(task.id(), now);
		store.insertEvent(request.id(), round.id(), task.id(), "QUESTION", actor.id(), task.originalUserId(), null,
				question, null, null, "APP", now);
		notices.authors("QUESTION_ASKED", request, actor.id(), task.stageName(), question);
		audit(actor, request, "APPROVAL_QUESTION", "IN_APPROVAL", question, task, "APP");
	}

	private void delegate(CurrentUser actor, RequestSnapshot request, ApprovalStore.Round round,
			List<ApprovalStore.Task> tasks, ApprovalStore.Task task, Long delegateTo, String reason, Instant now) {
		if (delegateTo == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("toUserId", "required")));
		}
		Set<Long> exclude = new HashSet<>();
		exclude.add(actor.id());
		tasks.stream().filter(ApprovalStore.Task::isPending).forEach(t -> exclude.add(t.assigneeUserId()));
		boolean eligible = resolver.eligibleDelegates(task, request, exclude)
			.stream()
			.anyMatch(h -> h.userId() == delegateTo);
		if (!eligible) {
			throw new ValidationFailedException(List.of(FieldViolation.of("toUserId", "delegate-not-eligible")));
		}
		supersedeQuestion(actor, request, round, task, now);
		store.decideTask(task.id(), "DELEGATED", actor.id(), now, reason);
		long original = task.originalUserId() != null ? task.originalUserId() : task.assigneeUserId();
		long newTaskId = store.insertTask(new ApprovalStore.NewTask(round.id(), request.id(), task.companyId(),
				task.stageNo(), task.stageName(), task.approverType(), task.policy(), delegateTo, original, "DELEGATED",
				task.slaMinutes(), task.escalationRole(), task.dueAt(), task.escalationAt(), now));
		requests.addParticipant(request.id(), delegateTo, "DELEGATE", now);
		store.insertEvent(request.id(), round.id(), task.id(), "DELEGATED", actor.id(), task.originalUserId(), delegateTo,
				reason, null, null, "APP", now);
		notices.task("TASK_DELEGATED", task.stageName(), newTaskId, delegateTo, request, actor.id(), now);
		long owner = tasks.stream()
			.filter(t -> t.isPending() && t.id() != task.id())
			.map(ApprovalStore.Task::assigneeUserId)
			.findFirst()
			.orElse(delegateTo);
		requests.assignOwner(request.id(), owner, now);
		audit(actor, request, "APPROVAL_DELEGATED", "IN_APPROVAL", reason, task, "APP");
	}

	/**
	 * Deciding while one's own question is open closes it (QA2-30): the requester is told, the SLA pause ends.
	 */
	private void supersedeQuestion(CurrentUser actor, RequestSnapshot request, ApprovalStore.Round round,
			ApprovalStore.Task task, Instant now) {
		if (task.questionOpen()) {
			store.insertEvent(request.id(), round.id(), task.id(), "QUESTION_SUPERSEDED", actor.id(), null, null, null,
					null, null, "APP", now);
			notices.authors("QUESTION_SUPERSEDED", request, actor.id(), task.stageName(), null);
		}
	}

	/**
	 * The requester (or entered-by user) answers an approver's question: the SLA resumes, shifted by the working
	 * time the question was open (S2-AC-05, S2-AC-09).
	 */
	@Transactional
	void answer(CurrentUser actor, long requestId, long taskId, String answer) {
		RequestSnapshot visible = requests.requireVisible(actor, requestId);
		if (!visible.isAuthor(actor.id())) {
			throw ApiProblemException.forbidden();
		}
		String clean = StringUtils.hasText(answer) ? answer.strip() : null;
		if (clean == null) {
			throw new ValidationFailedException(List.of(FieldViolation.of("answer", "required")));
		}
		if (clean.length() > 2000) {
			throw new ValidationFailedException(List.of(FieldViolation.of("answer", "too-long", "max", 2000)));
		}
		ApprovalStore.Task snapshot = store.task(taskId).filter(t -> t.requestId() == requestId)
			.orElseThrow(ApiProblemException::notFound);
		RequestSnapshot request = requests.lockForApproval(requestId);
		ApprovalStore.Round round = store.lockRound(snapshot.roundId());
		ApprovalStore.Task task = store.lockTasksOfRound(round.id())
			.stream()
			.filter(t -> t.id() == taskId)
			.findFirst()
			.orElseThrow(ApiProblemException::notFound);
		if (!task.isPending() || !task.questionOpen()) {
			throw ApiProblemException.conflict("no-open-question", "There is no open question on this task.");
		}
		Instant now = clock.instant();
		WorkingCalendar calendar = organization.workingCalendar(request.requestingCompanyId());
		long paused = task.pausedAt() == null ? 0 : calendar.workingMinutesBetween(task.pausedAt(), now);
		Instant due = paused == 0 ? task.dueAt() : calendar.addWorkingMinutes(task.dueAt(), paused);
		Instant escalation = paused == 0 ? task.escalationAt() : calendar.addWorkingMinutes(task.escalationAt(), paused);
		store.closeQuestion(task.id(), due, escalation);
		store.insertEvent(request.id(), round.id(), task.id(), "ANSWER", actor.id(), null, task.assigneeUserId(), clean,
				null, null, "APP", now);
		notices.user("QUESTION_ANSWERED", task.assigneeUserId(), request, actor.id(), task.stageName(), clean, null);
		audit(actor, request, "APPROVAL_ANSWER", "IN_APPROVAL", clean, task, "APP");
	}

	private void audit(CurrentUser actor, RequestSnapshot request, String action, String toStatus, String comment,
			ApprovalStore.Task task, String channel) {
		auditLog.record(new AuditRecord(actor.id(), actor.displayName(), request.requestingCompanyId(), ENTITY,
				Long.toString(request.id()), action, "IN_APPROVAL", toStatus, comment,
				"{\"taskId\":" + task.id() + ",\"stage\":" + task.stageNo() + ",\"onBehalfOf\":" + task.originalUserId()
						+ ",\"channel\":\"" + channel + "\"}"));
	}

	List<ApprovalStore.Stage> stagesOf(ApprovalStore.Round round) {
		return store.rule(round.ruleId()).map(r -> r.applicableStages(round.companyAmount())).orElse(new ArrayList<>());
	}

	record Basis(ApprovalStore.Rule rule, BigDecimal amount, BigDecimal rate, LocalDate rateDate) {
	}

}
