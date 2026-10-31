package com.epi.procurement.messaging;

import java.time.Instant;
import java.util.List;

final class MessagingViews {

	private MessagingViews() {
	}

	record ConversationSummary(long id, String contextType, long contextId, long supplierId, String supplierCode,
			String supplierName, String status, long requestId, long buyingCompanyId, int unreadCount,
			Instant createdAt, Instant lastMessageAt) {
	}

	record ConversationDetail(long id, String contextType, long contextId, long supplierId, String supplierCode,
			String supplierName, String status, boolean writable, long requestId, long buyingCompanyId,
			int unreadCount, Instant createdAt) {
	}

	record MessageView(long id, String audience, String bodyText, long senderUserId, String senderDisplayName,
			Instant createdAt) {
	}

	record MessagePage(List<MessageView> items, Long nextBeforeId) {
	}

	record OpenRequest(String contextType, long contextId, long supplierId) {
	}

	record SendRequest(String audience, String bodyText, String clientMessageId) {
	}

	record InviteContactRequest(String email, String displayName) {
	}

	record ContactView(long id, long userId, String email, String displayName, boolean active, Instant invitedAt,
			Instant revokedAt) {
	}

}
