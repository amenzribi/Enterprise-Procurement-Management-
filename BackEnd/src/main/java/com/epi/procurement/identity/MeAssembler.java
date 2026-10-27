package com.epi.procurement.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.epi.procurement.organization.OrganizationDirectory;

import jakarta.servlet.http.HttpSession;

/**
 * Builds {@link MeResponse}, including the coarse permissions the UI uses to show or hide actions. The server
 * still checks every action itself.
 */
@Component
class MeAssembler {

	private final OrganizationDirectory organization;

	private final SecuritySettings settings;

	MeAssembler(OrganizationDirectory organization, SecuritySettings settings) {
		this.organization = organization;
		this.settings = settings;
	}

	MeResponse assemble(CurrentUser user, HttpSession session) {
		MeResponse.Ref company = organization.findCompany(user.companyId())
			.map(c -> new MeResponse.Ref(c.id(), c.code(), c.name()))
			.orElse(new MeResponse.Ref(user.companyId(), "", ""));
		MeResponse.Ref department = user.departmentId() == null ? null
				: organization.findDepartment(user.departmentId())
					.map(d -> new MeResponse.Ref(d.id(), d.code(), d.name()))
					.orElse(null);
		return new MeResponse(user.id(), user.email(), user.displayName(), company, department,
				user.roles().stream().map(Role::name).toList(), permissions(user), user.preferredLanguage(),
				expiresAt(session), settings.session().idleTimeout().toSeconds(),
				settings.session().absoluteTimeout().toSeconds(), user.demo());
	}

	static List<String> permissions(CurrentUser user) {
		List<String> permissions = new ArrayList<>();
		if (user.hasRole(Role.REQUESTER) || user.hasRole(Role.ENTER_ON_BEHALF)) {
			permissions.add("REQUEST_CREATE");
		}
		if (user.hasRole(Role.ENTER_ON_BEHALF)) {
			permissions.add("REQUEST_ENTER_ON_BEHALF");
		}
		if (user.hasRole(Role.DEPARTMENT_MANAGER)) {
			permissions.add("REQUEST_VIEW_DEPARTMENT");
		}
		if (user.hasRole(Role.GROUP_MANAGEMENT)) {
			permissions.add("REQUEST_VIEW_GROUP");
		}
		if (user.hasRole(Role.ADMIN)) {
			permissions.add("SYSTEM_ADMIN");
		}
		return permissions;
	}

	private Instant expiresAt(HttpSession session) {
		if (session == null || session.getAttribute(SessionTimeouts.AUTHENTICATED_AT) == null) {
			return null;
		}
		return SessionTimeouts.expiresAt(session, settings.session());
	}

}
