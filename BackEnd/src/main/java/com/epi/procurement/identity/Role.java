package com.epi.procurement.identity;

import java.util.Optional;

/**
 * Roles of spec §20 implemented so far. Further roles (budget/technical validators, logistics, finance, sales,
 * external customer, auditor) are added with the sprints that use them; role codes are not constrained in the
 * database, so adding one needs no migration.
 */
public enum Role {

	REQUESTER, ENTER_ON_BEHALF, DEPARTMENT_MANAGER, BUYER, PURCHASING_MANAGER, GROUP_MANAGEMENT, ADMIN;

	static Optional<Role> parse(String code) {
		for (Role role : values()) {
			if (role.name().equals(code)) {
				return Optional.of(role);
			}
		}
		return Optional.empty();
	}

}
