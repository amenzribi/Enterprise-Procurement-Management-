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
		notifications.enqueue(new Notice("PASSWORD_" + event.purpose(), event.userId(), null,
				Map.of("displayName", event.displayName()), List.of(),
				List.of(new Notice.Action("mail.action.setPassword", placeholder)), "/sign-in"));
	}

}
