package com.epi.procurement.identity;

import java.util.List;

final class UserAdminViews {

	private UserAdminViews() {
	}

	record ListItem(long id, String email, String displayName, long companyId, String companyCode, boolean active) {
	}

	record GrantView(long id, String role, Long companyId, Long departmentId) {
	}

	record Detail(long id, String email, String displayName, long companyId, Long siteId, Long departmentId,
			String preferredLanguage, boolean active, boolean demo, int version, List<GrantView> roles,
			int pendingApprovals, int assignedBuying) {
	}

	record CreateRequest(String email, String displayName, long companyId, Long siteId, Long departmentId,
			String preferredLanguage, Boolean active, List<GrantRequest> roles) {
	}

	record UpdateRequest(String displayName, long companyId, Long siteId, Long departmentId, String preferredLanguage,
			int version) {
	}

	record GrantRequest(String role, Long companyId, Long departmentId) {
	}

	record StatusResult(long userId, boolean active, int pendingApprovals, int assignedBuying) {
	}

	record ActiveRequest(boolean active) {
	}

	/** Temporary password set by an administrator; the user must change it at next sign-in. */

}
