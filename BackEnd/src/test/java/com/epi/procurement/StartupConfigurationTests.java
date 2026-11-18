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
			.run("--app.environment=production", "--app.mail.mode=smtp",
					"--app.public-base-url=https://procurement.example.org"))
			.hasStackTraceContaining("set the DB_URL environment variable");
	}

	/** Audit S-15: the way every instance was started so far must not work behind a public HTTPS address. */
	@Test
	void startupIsRefusedWithTheDevProfileAndAPublicHttpsAddress() {
		assertThatThrownBy(() -> new SpringApplicationBuilder(ProcurementApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("dev", "demo")
			.run("--app.public-base-url=https://uat.example.org"))
			.hasStackTraceContaining("Unsafe configuration")
			.hasStackTraceContaining("development profile");
	}

	/** Audit S-16: no silent default that writes e-mails with live links to disk. */
	@Test
	void startupIsRefusedOutsideDevelopmentWithoutAMailMode() {
		assumeTrue(System.getenv("MAIL_MODE") == null, "MAIL_MODE is set in this environment");

		assertThatThrownBy(() -> new SpringApplicationBuilder(ProcurementApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("uat")
			.run("--spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=none",
					"--app.public-base-url=https://uat.example.org", "--app.environment=uat"))
			.hasStackTraceContaining("MAIL_MODE is not set");
	}

	/** Review R-4: the developer command line on a server, with no declaration at all, must not start. */
	@Test
	void startupIsRefusedWithTheDevProfileAgainstADatabaseServer() {
		assertThatThrownBy(() -> new SpringApplicationBuilder(ProcurementApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("dev", "demo")
			.run("--spring.datasource.url=jdbc:sqlserver://sql-uat.corp.example:1433;databaseName=procurement"))
			.hasStackTraceContaining("Unsafe configuration")
			.hasStackTraceContaining("only against a database on this machine");
	}

	/** Review R-4: a declared deployed environment refuses the development and demo profiles whatever the address. */
	@Test
	void startupIsRefusedWithDevOrDemoProfilesInADeclaredEnvironment() {
		assertThatThrownBy(() -> new SpringApplicationBuilder(ProcurementApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("dev", "demo")
			.run("--app.environment=uat", "--app.public-base-url=http://localhost:4200"))
			.hasStackTraceContaining("never accepted in a deployed environment");
		assertThatThrownBy(() -> new SpringApplicationBuilder(ProcurementApplication.class)
			.web(WebApplicationType.NONE)
			.profiles("demo")
			.run("--app.environment=production", "--app.mail.mode=smtp",
					"--app.public-base-url=https://procurement.example.org",
					"--spring.datasource.url=jdbc:sqlserver://sql.corp.example:1433;databaseName=procurement"))
			.hasStackTraceContaining("demo profile");
	}

}
