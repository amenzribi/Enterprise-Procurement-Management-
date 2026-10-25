package com.epi.procurement.audit;

import java.util.Objects;

/**
 * An auditable action to append to the trail. Status fields are optional and describe a state transition.
 */
public record AuditRecord(Long actorUserId, String actorLabel, Long companyId, String entityType, String entityId,
		String action, String fromStatus, String toStatus, String comment, String payloadJson) {

	public AuditRecord {
		Objects.requireNonNull(actorLabel, "actorLabel");
		Objects.requireNonNull(entityType, "entityType");
		Objects.requireNonNull(entityId, "entityId");
		Objects.requireNonNull(action, "action");
	}

}
