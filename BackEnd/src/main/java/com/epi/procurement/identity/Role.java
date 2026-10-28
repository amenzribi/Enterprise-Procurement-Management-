package com.epi.procurement.identity;

import java.util.Optional;

/**
 * Roles of spec §20 / Solution Guide v3.0 §4 implemented so far. BUDGET_APPROVER and MANAGEMENT_APPROVER are
 * approval-stage roles (Sprint 2). Further roles (technical evaluator, logistics, receiver, finance, auditor) are
 * added with the sprints that use them; role codes are not constrained in the database.
 */
public enum Role {

	REQUESTER, ENTER_ON_BEHALF, DEPARTMENT_MANAGER, BUDGET_APPROVER, MANAGEMENT_APPROVER, BUYER, PURCHASING_MANAGER,
	GROUP_MANAGEMENT, ADMIN;

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
