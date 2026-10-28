package com.epi.procurement.request;

import java.time.Instant;

/**
 * Published synchronously inside the submission transaction, after the request row is locked and moved to
 * IN_APPROVAL and before the audit event (lock order request -> round -> tasks -> audit). A listener failure rolls
 * the whole submission back (no number, no counter increment, no audit).
 *
 * @param resubmission true when the request was returned for revision and is submitted again (new round)
 */
public record RequestSubmittedEvent(long requestId, int roundNo, long actorUserId, Instant submittedAt,
		boolean resubmission) {
}
