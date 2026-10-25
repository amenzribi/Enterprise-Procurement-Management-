package com.epi.procurement.audit;

/**
 * Result of re-computing audit hash chains. When the chain is invalid, {@code firstInvalidEventId} identifies
 * the first altered or out-of-sequence event; it is null when the chain is intact or when events were removed
 * from its end (detected through the chain head).
 */
public record AuditChainVerification(boolean valid, long eventsChecked, Long firstInvalidEventId) {
}
