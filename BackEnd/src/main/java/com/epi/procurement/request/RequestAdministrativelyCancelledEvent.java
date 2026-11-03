package com.epi.procurement.request;

import java.time.Instant;

/**
 * Published when GLOBAL_ADMIN cancels or returns a request administratively. The approval module cancels pending
 * tasks and invalidates unused one-tap links in the same transaction.
 */
public record RequestAdministrativelyCancelledEvent(long requestId, long actorUserId, Instant occurredAt,
		String outcome) {
}
