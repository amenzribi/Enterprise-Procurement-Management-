package com.epi.procurement.messaging;

import java.util.Set;

/**
 * Published inside the transaction that stored an internal message or a participant change; delivered to live streams
 * only after that transaction commits ({@link InternalMessageStream}).
 *
 * <p><b>Extension point for e-mail.</b> A future e-mail digest listens to this event with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} and writes to the notification outbox, re-checking each
 * recipient's access as the stream does. No listener sends e-mail in this release (docs/architecture/internal-messaging.md).
 *
 * @param recipientUserIds every current participant except the sender, as known when the message was stored
 */
public record InternalMessagePosted(long conversationId, long messageId, long senderUserId,
		Set<Long> recipientUserIds) {
}
