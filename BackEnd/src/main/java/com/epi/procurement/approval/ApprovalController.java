package com.epi.procurement.approval;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.Idempotency;

/**
 * Approver inbox, task actions, request tracker data, approver preview and answers to questions (S2-AC-04..11).
 * State-changing calls accept an {@code Idempotency-Key} header (S2-AC-13).
 */
@RestController
@RequestMapping("/api/v1")
class ApprovalController {

	private final ApprovalEngine engine;

	private final ApprovalQueries queries;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	ApprovalController(ApprovalEngine engine, ApprovalQueries queries, CurrentUserProvider currentUser,
			Idempotency idempotency) {
		this.engine = engine;
		this.queries = queries;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/approvals/inbox")
	ApprovalViews.InboxPage inbox(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return queries.inbox(currentUser.require(), status, q, sort, page, size);
	}

	@GetMapping("/approvals/inbox/count")
	ApprovalViews.InboxCount count() {
		return queries.count(currentUser.require());
	}

	@PostMapping("/approvals/tasks/{id}/approve")
	ResponseEntity<?> approve(@PathVariable long id, @RequestBody DecisionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		return act(id, ApprovalEngine.Action.APPROVE, body.comment(), body.version(), null, key, "approve", body);
	}

	@PostMapping("/approvals/tasks/{id}/reject")
	ResponseEntity<?> reject(@PathVariable long id, @RequestBody DecisionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		return act(id, ApprovalEngine.Action.REJECT, body.reason(), body.version(), null, key, "reject", body);
	}

	@PostMapping("/approvals/tasks/{id}/return")
	ResponseEntity<?> returnForRevision(@PathVariable long id, @RequestBody DecisionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		return act(id, ApprovalEngine.Action.RETURN, body.reason(), body.version(), null, key, "return", body);
	}

	@PostMapping("/approvals/tasks/{id}/question")
	ResponseEntity<?> question(@PathVariable long id, @RequestBody DecisionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		return act(id, ApprovalEngine.Action.QUESTION, body.question(), body.version(), null, key, "question", body);
	}

	@PostMapping("/approvals/tasks/{id}/delegate")
	ResponseEntity<?> delegate(@PathVariable long id, @RequestBody DecisionBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		return act(id, ApprovalEngine.Action.DELEGATE, body.reason(), body.version(), body.toUserId(), key, "delegate",
				body);
	}

	@GetMapping("/approvals/tasks/{id}/delegates")
	List<ApprovalViews.Delegate> delegates(@PathVariable long id) {
		return queries.delegates(currentUser.require(), id);
	}

	@GetMapping("/requests/{id}/approval")
	ApprovalViews.RequestApproval requestApproval(@PathVariable long id) {
		return queries.requestApproval(currentUser.require(), id);
	}

	@GetMapping("/requests/{id}/approval-preview")
	ApprovalViews.Preview preview(@PathVariable long id) {
		return queries.preview(currentUser.require(), id);
	}

	@PostMapping("/requests/{id}/answers")
	ResponseEntity<?> answer(@PathVariable long id, @RequestBody AnswerBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "answer|" + id + "|" + body, () -> {
			engine.answer(user, id, body.taskId() == null ? -1 : body.taskId(), body.answer());
			return ResponseEntity.ok(queries.requestApproval(user, id));
		});
	}

	private ResponseEntity<?> act(long taskId, ApprovalEngine.Action action, String text, Integer version,
			Long delegateTo, String key, String name, DecisionBody body) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, name + "|" + taskId + "|" + body, () -> {
			long requestId = engine.decide(user, taskId, version, action, text, delegateTo, "APP");
			return ResponseEntity.ok(queries.requestApproval(user, requestId));
		});
	}

	/**
	 * Body of task actions: {@code version} (required) plus {@code comment} (approve), {@code reason} (reject,
	 * return, optional for delegate), {@code question} (question), {@code toUserId} (delegate).
	 */
	record DecisionBody(Integer version, String comment, String reason, String question, Long toUserId) {
	}

	record AnswerBody(Long taskId, String answer) {
	}

}
