package com.epi.procurement.approval;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response bodies of the approval API. Instants are ISO-8601 UTC; amounts are rounded to the currency's minor units.
 */
final class ApprovalViews {

	private ApprovalViews() {
	}

	record UserRef(long id, String displayName) {
	}

	record Ref(long id, String code, String name) {
	}

	record InboxItem(long taskId, int taskVersion, String status, long requestId, String number, String title,
			String userStatus, UserRef requester, Ref company, BigDecimal amount, String currency,
			BigDecimal companyAmount, String companyCurrency, int stageNo, String stageName, String policy,
			Instant waitingSince, Instant dueAt, boolean overdue, boolean questionOpen, UserRef onBehalfOf,
			Instant decidedAt, LocalDate requiredDate) {
	}

	record InboxPage(List<InboxItem> items, int page, int size, long totalItems, int totalPages) {
	}

	record InboxCount(long pending, long overdue) {
	}

	record TaskView(long id, int version, UserRef assignee, UserRef onBehalfOf, String routingNote, String status,
			Instant createdAt, Instant dueAt, Instant escalationAt, boolean overdue, boolean questionOpen,
			Instant decidedAt, UserRef decidedBy, String comment) {
	}

	record StageView(int stageNo, String name, String policy, String approverType, int slaHours, String status,
			List<TaskView> tasks) {
	}

	record Amounts(BigDecimal requestAmount, String requestCurrency, BigDecimal companyAmount,
			String companyCurrency, BigDecimal rate, LocalDate rateDate) {
	}

	record RoundView(long id, int roundNo, String status, String ruleName, int ruleVersion, Amounts amounts,
			int currentStageNo, Instant startedAt, Instant completedAt, List<StageView> stages) {
	}

	record EventView(long id, int roundNo, Long taskId, String kind, UserRef actor, UserRef onBehalfOf,
			UserRef target, String text, String fromStatus, String toStatus, String channel, Instant occurredAt) {
	}

	record MyTask(long id, int version, int stageNo, String stageName, boolean questionOpen) {
	}

	record OpenQuestion(long taskId, UserRef askedBy, String question, Instant askedAt) {
	}

	/**
	 * Tracker data of a request (S2-AC-11) with the actions available to the caller (S2DR-17).
	 *
	 * @param actions subset of APPROVE, REJECT, RETURN, QUESTION, DELEGATE (holder of a pending task) and ANSWER
	 * (author, while a question is open)
	 */
	record RequestApproval(long requestId, String status, String userStatus, Integer currentRoundNo,
			List<UserRef> nextActors, Instant waitingSince, List<RoundView> rounds, List<EventView> events,
			List<String> actions, MyTask myTask, List<OpenQuestion> openQuestions) {
	}

	record PreviewStage(int stageNo, String name, String policy, String approverType, List<PreviewApprover> approvers) {
	}

	record PreviewApprover(UserRef approver, UserRef onBehalfOf, String routingNote) {
	}

	/**
	 * Who will approve, before submission (S2DR-17). {@code problem} is the error code a submission would get now.
	 */
	record Preview(String problem, String ruleName, Amounts amounts, List<PreviewStage> stages) {
	}

	record Delegate(long id, String displayName) {
	}

	record Absence(long id, UserRef user, UserRef delegate, Instant startsAt, Instant endsAt, String reason,
			boolean adminOverride, UserRef createdBy, Instant createdAt, boolean active) {
	}

	record RuleStage(int stageNo, String name, String approverType, Long approverUserId, String policy, int slaHours,
			String escalationRole, BigDecimal minAmount) {
	}

	record Rule(long id, long ruleKey, int versionNo, String status, Ref company, String name, Long categoryId,
			String purchaseType, BigDecimal minAmount, BigDecimal maxAmount, Instant createdAt, Instant activatedAt,
			boolean used, List<RuleStage> stages) {
	}

	record Rate(long id, String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate rateDate, String source,
			Instant createdAt) {
	}

	record AwaitingCircuit(long requestId, String number, String title, Ref company, Instant submittedAt) {
	}

	record LinkSummary(String number, String title, UserRef requester, Ref company, BigDecimal amount, String currency,
			int currencyMinorUnits, LocalDate requiredDate, String stageName, List<LinkLine> lines, int lineCount,
			Instant expiresAt) {
	}

	record LinkLine(String description, BigDecimal quantity, String unit) {
	}

	record LinkResult(String outcome, String number) {
	}

}
