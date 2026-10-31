package com.epi.procurement.messaging;

/**
 * Published after a message is saved so notifications can notify peers without coupling modules.
 */
public record MessagePosted(long conversationId, long messageId, String audience, long senderUserId,
		String senderDisplayName, long requestId, long buyingCompanyId, long supplierId, boolean supplierSender) {
}
