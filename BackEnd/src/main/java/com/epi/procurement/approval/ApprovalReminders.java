package com.epi.procurement.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.request.RequestSnapshot;
import com.epi.procurement.request.RequestWorkflow;

/**
 * Reminders when a task reaches its SLA and escalations at twice the SLA (S2-AC-09). SLA time is working time in
 * the requesting company's calendar and excludes open questions (tasks with an open question are skipped; the due
 * times are shifted when the question is answered). Each reminder is sent once: the unique (task, kind) record is
 * claimed in the same transaction as the notification (S2DR-10).
 */
@Component
class ApprovalReminders {

	private static final Logger log = LoggerFactory.getLogger(ApprovalReminders.class);

	private final ApprovalStore store;

	private final ApproverResolver resolver;

	private final RequestWorkflow requests;

	private final ApprovalNotices notices;

	private final UserDirectory users;

	private final TransactionTemplate transaction;

	private final Clock clock;

	ApprovalReminders(ApprovalStore store, ApproverResolver resolver, RequestWorkflow requests, ApprovalNotices notices,
			UserDirectory users, PlatformTransactionManager transactionManager, ObjectProvider<Clock> clock) {
		this.store = store;
		this.resolver = resolver;
		this.requests = requests;
		this.notices = notices;
		this.users = users;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Sends due reminders and escalations; returns how many were sent.
	 */
	int run() {
		Instant now = clock.instant();
		int sent = 0;
		for (String kind : List.of("REMINDER", "ESCALATION")) {
			for (ApprovalStore.Task task : store.dueForReminder(kind, now)) {
				Boolean done = transaction.execute(status -> send(kind, task, now));
				if (Boolean.TRUE.equals(done)) {
					sent++;
				}
			}
		}
		if (sent > 0) {
			log.info("Approval reminders sent: {}", sent);
		}
		return sent;
	}

	private boolean send(String kind, ApprovalStore.Task due, Instant now) {
		// Re-check under the task lock (S2CR-04): decided, paused by a question or rescheduled meanwhile.
		ApprovalStore.Task task = store.lockTask(due.id());
		Instant deadline = kind.equals("REMINDER") ? task.dueAt() : task.escalationAt();
		if (!task.isPending() || task.questionOpen() || deadline.isAfter(now)) {
			return false;
		}
		if (!store.claimReminder(task.id(), kind, now)) {
			return false;
		}
		RequestSnapshot request = requests.find(task.requestId()).orElse(null);
		if (request == null) {
			return false;
		}
		if (kind.equals("REMINDER")) {
			notices.task("TASK_REMINDER", task.stageName(), task.id(), task.assigneeUserId(), request, null, now);
		}
		else {
			List<Long> contacts = resolver.escalationContacts(task.escalationRole(), request)
				.stream()
				.filter(id -> id != task.assigneeUserId())
				.toList();
			for (long contact : contacts) {
				requests.addParticipant(request.id(), contact, "ESCALATION", now);
				notices.user("TASK_ESCALATION", contact, request, null, task.stageName(), null, task.assigneeUserId());
			}
			if (contacts.isEmpty()) {
				// Administrators act through configuration: informed, but not given business data access (S2CR-05).
				for (long admin : users.administratorsOfGroup(request.groupId())) {
					notices.user("TASK_ESCALATION", admin, request, null, task.stageName(), null, task.assigneeUserId());
				}
			}
		}
		store.insertEvent(request.id(), task.roundId(), task.id(), kind.equals("REMINDER") ? "REMINDER" : "ESCALATED",
				null, task.originalUserId(), task.assigneeUserId(), null, null, null, "APP", now);
		return true;
	}

}
