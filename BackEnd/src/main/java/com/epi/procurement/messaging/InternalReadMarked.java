package com.epi.procurement.messaging;

/**
 * A user moved their read marker. Sent to that user's other open tabs after commit, so their unread counts follow.
 */
record InternalReadMarked(long userId, long conversationId) {
}
