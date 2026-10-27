package com.epi.procurement.identity;

/**
 * Public view of a user account for other modules (e.g. a request's requester or owner).
 */
public record UserSummary(long id, String displayName, String email, long companyId, Long departmentId,
		boolean active) {
}
