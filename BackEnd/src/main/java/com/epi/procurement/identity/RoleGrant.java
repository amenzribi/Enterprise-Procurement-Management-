package com.epi.procurement.identity;

/**
 * A role held in a company (and optionally a department). {@code companyId == null} means every company of the
 * user's group and is only stored for group-wide roles (GROUP_MANAGEMENT, ADMIN).
 */
public record RoleGrant(Role role, Long companyId, Long departmentId) {

	public boolean appliesToCompany(long company) {
		return companyId == null || companyId == company;
	}

}
