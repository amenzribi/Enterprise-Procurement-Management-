package com.epi.procurement.audit;

import java.time.Instant;

/** Read model of one audit event for packs and admin views. */
public record AuditEventView(long id, Instant occurredAt, String actorLabel, Long companyId, String action,
		String fromStatus, String toStatus, String comment) {
}
