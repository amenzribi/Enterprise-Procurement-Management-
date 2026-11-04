package com.epi.procurement.identity;

import java.util.Optional;

/**
 * Roles of spec §20 / Solution Guide v3.0 §4 implemented so far. BUDGET_APPROVER and MANAGEMENT_APPROVER are
 * approval-stage roles (Sprint 2). RECEIVER is Sprint 7. Further roles (logistics, finance, auditor) are
 * added with the sprints that use them; role codes are not constrained in the database.
 */
public enum Role {

	REQUESTER, ENTER_ON_BEHALF, DEPARTMENT_MANAGER, BUDGET_APPROVER, MANAGEMENT_APPROVER, BUYER, PURCHASING_MANAGER,
	RECEIVER, GROUP_MANAGEMENT, ADMIN, GLOBAL_ADMIN, SUPPLIER_CONTACT;

	/** Installation-scoped administration (Sprint 10). Group-wide grant only. */
	public boolean isGlobalAdmin() {
		return this == GLOBAL_ADMIN;
	}

	/** External supplier portal principal (Sprint 11). Group-wide grant; access gated by supplier_contact. */
	public boolean isSupplierContact() {
		return this == SUPPLIER_CONTACT;
	}

	/** Roles that may be granted with company_id NULL. ADMIN is company-scoped (requires company_id). */
	public boolean isGroupWide() {
		return this == GROUP_MANAGEMENT || this == GLOBAL_ADMIN || this == SUPPLIER_CONTACT;
	}

	/** Roles that can hold approval tasks (approval rule stages and escalation). */
	public boolean isApproverRole() {
		return this == DEPARTMENT_MANAGER || this == BUDGET_APPROVER || this == MANAGEMENT_APPROVER;
	}

	public static Optional<Role> parse(String code) {
		for (Role role : values()) {
			if (role.name().equals(code)) {
				return Optional.of(role);
			}
		}
		return Optional.empty();
	}

}
