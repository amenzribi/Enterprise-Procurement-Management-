package com.epi.procurement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MeAssemblerRequestAdminTest {

	@Test
	void globalAdminReceivesRequestAdminWithoutPurchasingPowers() {
		CurrentUser admin = new CurrentUser(1L, "ga@example.com", "Global Admin", 10L, 1L, null, null, "en", false, 1,
				false, List.of(new RoleGrant(Role.GLOBAL_ADMIN, null, null)));
		List<String> permissions = MeAssembler.permissions(admin);
		assertThat(permissions).contains("REQUEST_ADMIN", "REQUEST_DRAFT_ADMIN", "SYSTEM_ADMIN", "APPROVAL_ADMIN",
				"ORG_ADMIN", "USER_ADMIN");
		assertThat(permissions).doesNotContain("PURCHASING_ACT", "APPROVAL_ACT", "MESSAGING_BUYER", "ASSISTANT_USE");
	}

	@Test
	void companyAdminReceivesDraftAdminButNotElevatedRequestAdmin() {
		CurrentUser admin = new CurrentUser(2L, "admin@example.com", "Admin", 10L, 1L, null, null, "en", false, 1, false,
				List.of(new RoleGrant(Role.ADMIN, 10L, null)));
		List<String> permissions = MeAssembler.permissions(admin);
		assertThat(permissions).contains("SYSTEM_ADMIN", "APPROVAL_ADMIN", "REQUEST_DRAFT_ADMIN");
		assertThat(permissions).doesNotContain("REQUEST_ADMIN");
	}

}
