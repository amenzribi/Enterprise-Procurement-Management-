package com.epi.procurement.notification;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.epi.procurement.identity.EmailAddressChanged;
import com.epi.procurement.identity.EmailChangeVerificationRequested;

/**
 * E-mail correction mails (Sprint 2): the proof-of-control link to the NEW address; after the change, a security
 * notice to the OLD address. Both are queued in the transaction of the change.
 */
@Component
class EmailChangeMailListener {

	private final Notifications notifications;

	EmailChangeMailListener(Notifications notifications) {
		this.notifications = notifications;
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	void onVerificationRequested(EmailChangeVerificationRequested event) {
		notifications.enqueueToAddress(new Notice("EMAIL_CHANGE_CONFIRM", event.userId(), null,
				Map.of("displayName", event.displayName()), List.of(),
				List.of(new Notice.Action("mail.action.confirmEmail",
						NoticeLinkResolver.placeholder(EmailChangeLinkResolver.KIND, event.changeId()))),
				"/sign-in"), event.newEmail());
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	void onAddressChanged(EmailAddressChanged event) {
		notifications.enqueueToAddress(new Notice("EMAIL_CHANGED", event.userId(), null,
				Map.of("displayName", event.displayName(), "newEmail", event.newEmail()), List.of(), List.of(), "/sign-in"),
				event.oldEmail());
	}

}
