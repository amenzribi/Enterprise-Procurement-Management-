package com.epi.procurement.notification;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.epi.procurement.messaging.MessagePosted;

/**
 * Notifies peers after a message commits. Never includes message body or tokens (ADR-011).
 */
@Component
class MessagePostedListener {

	private final Notifications notifications;

	private final JdbcTemplate jdbc;

	MessagePostedListener(Notifications notifications, DataSource dataSource) {
		this.notifications = notifications;
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onMessagePosted(MessagePosted event) {
		if ("INTERNAL".equals(event.audience())) {
			notifyInternalParticipants(event);
			return;
		}
		if (event.supplierSender()) {
			notifyInternalParticipants(event);
		}
		else {
			notifySupplierContacts(event);
			notifyInternalParticipants(event);
		}
	}

	private void notifyInternalParticipants(MessagePosted event) {
		List<Long> recipients = jdbc.queryForList("""
				SELECT DISTINCT p.user_id
				FROM conversation_participant p
				JOIN user_account u ON u.id = p.user_id AND u.is_active = 1
				WHERE p.conversation_id = ? AND p.participant_kind = 'INTERNAL' AND p.user_id <> ?""", Long.class,
				event.conversationId(), event.senderUserId());
		for (Long userId : recipients) {
			notifications.enqueueIndependently(new Notice("MESSAGE_RECEIVED", userId, event.requestId(),
					Map.of("actor", event.senderDisplayName(), "number", Long.toString(event.requestId()), "title",
							"Supplier conversation"),
					List.of(), List.of(new Notice.Action("mail.action.open", "/messages/" + event.conversationId())),
					"/messages/" + event.conversationId()));
		}
	}

	private void notifySupplierContacts(MessagePosted event) {
		List<Long> recipients = jdbc.queryForList("""
				SELECT sc.user_id FROM supplier_contact sc
				JOIN user_account u ON u.id = sc.user_id AND u.is_active = 1
				WHERE sc.supplier_id = ? AND sc.is_active = 1 AND sc.revoked_at IS NULL AND sc.user_id <> ?""",
				Long.class, event.supplierId(), event.senderUserId());
		for (Long userId : recipients) {
			notifications.enqueueIndependently(new Notice("MESSAGE_SUPPLIER", userId, event.requestId(),
					Map.of("actor", event.senderDisplayName(), "number", Long.toString(event.requestId()), "title",
							"Buyer message"),
					List.of(),
					List.of(new Notice.Action("mail.action.open", "/supplier/messages/" + event.conversationId())),
					"/supplier/messages/" + event.conversationId()));
		}
	}

}
