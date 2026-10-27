package com.epi.procurement.identity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The authenticated user with their organisation and role grants, resolved from the session on every request and
 * never from client-supplied identifiers (ADR-007).
 */
public record CurrentUser(long id, String email, String displayName, long companyId, long groupId, Long departmentId,
		Long siteId, String preferredLanguage, boolean demo, List<RoleGrant> grants) {

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

}
