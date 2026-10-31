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
				settings.session().absoluteTimeout().toSeconds(), user.demo(), user.mustChangePassword());
	}

	static List<String> permissions(CurrentUser user) {
		List<String> permissions = new ArrayList<>();
		if (user.hasRole(Role.SUPPLIER_CONTACT)) {
			permissions.add("MESSAGING_SUPPLIER");
			return permissions;
		}
		if (user.isGlobalAdmin()) {
			permissions.add("USER_ADMIN");
			permissions.add("USER_ADMIN_GLOBAL");
			permissions.add("ORG_ADMIN");
			permissions.add("SYSTEM_ADMIN");
		}
		if (user.hasRole(Role.ADMIN) && !permissions.contains("USER_ADMIN")) {
			permissions.add("USER_ADMIN");
		}
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
		if (user.grants().stream().anyMatch(g -> g.role().isApproverRole())) {
			permissions.add("APPROVAL_ACT");
		}
		if (user.hasRole(Role.BUYER) || user.hasRole(Role.PURCHASING_MANAGER)) {
			permissions.add("PURCHASING_ACT");
			permissions.add("MESSAGING_BUYER");
		}
		if (user.hasRole(Role.PURCHASING_MANAGER)) {
			permissions.add("PURCHASING_MANAGE");
		}
		if (user.hasRole(Role.RECEIVER)) {
			permissions.add("RECEIPT_ACT");
		}
		if (user.hasRole(Role.DEPARTMENT_MANAGER) || user.hasRole(Role.BUYER) || user.hasRole(Role.PURCHASING_MANAGER)
				|| user.hasRole(Role.GROUP_MANAGEMENT) || user.hasRole(Role.ADMIN)) {
			permissions.add("DASHBOARD_VIEW");
		}
		if (user.hasRole(Role.PURCHASING_MANAGER) || user.hasRole(Role.ADMIN) || user.hasRole(Role.GROUP_MANAGEMENT)) {
			permissions.add("ERP_EXPORT");
		}
		if (user.hasRole(Role.ADMIN)) {
			permissions.add("SYSTEM_ADMIN");
			permissions.add("APPROVAL_ADMIN");
			permissions.add("ORG_ADMIN");
			permissions.add("IMPORT_RUN");
		}
		if (user.hasRole(Role.BUYER) || user.hasRole(Role.PURCHASING_MANAGER) || user.hasRole(Role.ADMIN)
				|| user.hasRole(Role.GROUP_MANAGEMENT)) {
			permissions.add("SUPPLIER_READ");
		}
		if (user.hasRole(Role.PURCHASING_MANAGER) || user.hasRole(Role.ADMIN)) {
			permissions.add("SUPPLIER_MANAGE");
			if (!permissions.contains("IMPORT_RUN")) {
				permissions.add("IMPORT_RUN");
			}
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
