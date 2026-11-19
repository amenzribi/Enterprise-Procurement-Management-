package com.epi.procurement.notification;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.epi.procurement.identity.PasswordSetupRequested;

@Component
class PasswordResetMailListener {

	private final Notifications notifications;

	PasswordResetMailListener(Notifications notifications) {
		this.notifications = notifications;
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	void onPasswordSetupRequested(PasswordSetupRequested event) {
		Long createdBy = event.actorUserId() == null ? 0L : event.actorUserId();
		String placeholder = NoticeLinkResolver.placeholder(PasswordResetLinkResolver.KIND, event.userId(),
				event.purpose(), createdBy);
		// ADR-016: the owner always learns who asked. A reset requested by an administrator says so; one the owner asked
		// for on the sign-in page says that instead, and how to ignore it.
		String kind = "RESET".equals(event.purpose()) && event.actorUserId() == null ? "PASSWORD_RESET_SELF"
				: "PASSWORD_" + event.purpose();
		notifications.enqueue(new Notice(kind, event.userId(), null,
				Map.of("displayName", event.displayName()), List.of(),
				List.of(new Notice.Action("mail.action.setPassword", placeholder)), "/sign-in"));
	}

}
