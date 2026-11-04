package com.epi.procurement.identity;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The authenticated user with their organisation and role grants, resolved from the session on every request and
 * never from client-supplied identifiers (ADR-007).
 */
public record CurrentUser(long id, String email, String displayName, long companyId, long groupId, Long departmentId,
		Long siteId, String preferredLanguage, boolean demo, int credentialsVersion, boolean mustChangePassword,
		List<RoleGrant> grants) {

	public CurrentUser {
		grants = List.copyOf(grants);
	}

	public boolean hasRole(Role role) {
		return grants.stream().anyMatch(g -> g.role() == role);
	}

	public boolean hasRoleIn(Role role, long company) {
		return grants.stream().anyMatch(g -> g.role() == role && g.appliesToCompany(company));
	}

	public List<RoleGrant> grants(Role role) {
		return grants.stream().filter(g -> g.role() == role).toList();
	}

	public Set<Role> roles() {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		grants.forEach(g -> roles.add(g.role()));
		return roles;
	}

	public boolean isGlobalAdmin() {
		return hasRole(Role.GLOBAL_ADMIN);
	}

	/** Company-scoped ADMIN (one or more explicit company grants). */
	public boolean isCompanyAdmin() {
		return !isGlobalAdmin() && hasRole(Role.ADMIN);
	}

	/** Companies this ADMIN may administer. Empty when not a company ADMIN. */
	public Set<Long> adminCompanyIds() {
		Set<Long> ids = new LinkedHashSet<>();
		for (RoleGrant grant : grants) {
			if (grant.role() == Role.ADMIN && grant.companyId() != null) {
				ids.add(grant.companyId());
			}
		}
		return Set.copyOf(ids);
	}

	public boolean canAdminCompany(long companyId) {
		return isGlobalAdmin() || hasRoleIn(Role.ADMIN, companyId);
	}

}
