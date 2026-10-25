package com.epi.procurement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class StartupConfigurationTests {

	@Test
	void startupFailsFastWithoutDbUrlOutsideDevProfiles() {
		assumeTrue(System.getenv("DB_URL") == null, "DB_URL is set in this environment");

		assertThatThrownBy(() -> new SpringApplicationBuilder(ProcurementApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("prod")
			.run())
			.hasStackTraceContaining("set the DB_URL environment variable");
	}

}
