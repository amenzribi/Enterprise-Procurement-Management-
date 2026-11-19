package com.epi.procurement.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Audit S-15, S-16 and review R-4: an unsafe combination of profile and settings stops the application from
 * starting, and the decision rests on the declared environment and the database location, not on the public URL.
 */
class DeploymentConfigurationGuardTests {

	private static final String LOCAL_DB = "jdbc:sqlserver://localhost:1433;databaseName=procurement_dev;integratedSecurity=true";

	private static final String SERVER_DB = "jdbc:sqlserver://sql-uat.corp.example:1433;databaseName=procurement;encrypt=true";

	private static MockEnvironment deployed(String environment) {
		return new MockEnvironment().withProperty("app.environment", environment)
			.withProperty("app.public-base-url", "https://procurement.example.org")
			.withProperty("app.mail.mode", "smtp")
			.withProperty("app.security.cookie-secure", "true")
			.withProperty("app.attachments.clamav.host", "127.0.0.1")
			.withProperty("management.server.port", "9091")
			.withProperty("management.server.address", "10.10.0.5")
			.withProperty("spring.datasource.url", SERVER_DB);
	}

	private static MockEnvironment developer(String databaseUrl, String... profiles) {
		MockEnvironment dev = new MockEnvironment().withProperty("app.public-base-url", "http://localhost:4200")
			.withProperty("app.mail.mode", "file")
			.withProperty("app.security.cookie-secure", "false")
			.withProperty("app.testing.endpoints", "true")
			.withProperty("server.address", "127.0.0.1")
			.withProperty("spring.datasource.url", databaseUrl);
		dev.setActiveProfiles(profiles);
		return dev;
	}

	// ---- accepted

	@Test
	void everyDeployedEnvironmentIsAcceptedWhenComplete() {
		for (String environment : new String[] { "uat", "staging", "preprod", "production", "PRODUCTION" }) {
			assertThat(DeploymentConfigurationGuard.problems(deployed(environment))).as(environment).isEmpty();
		}
	}

	@Test
	void aDeveloperMachineWithALocalDatabaseIsAccepted() {
		for (String url : new String[] { LOCAL_DB, "jdbc:sqlserver://127.0.0.1;databaseName=x",
				"jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=x", "jdbc:sqlserver://LOCALHOST:1433;databaseName=x",
				"jdbc:sqlserver://(local);databaseName=x" }) {
			assertThat(DeploymentConfigurationGuard.problems(developer(url, "dev", "demo"))).as(url).isEmpty();
		}
		assertThat(DeploymentConfigurationGuard.problems(developer(LOCAL_DB, "test", "demo"))).isEmpty();
	}

	@Test
	void aDeveloperMachineWithARemoteDatabaseMustSayItIsDevelopment() {
		assertThat(DeploymentConfigurationGuard
			.problems(developer(SERVER_DB, "dev").withProperty("app.environment", "development"))).isEmpty();
	}

	@Test
	void aDemonstrationServerMayUseTheDemoProfileButNotADevelopmentProfile() {
		MockEnvironment demo = deployed("demo");
		demo.setActiveProfiles("demo");
		assertThat(DeploymentConfigurationGuard.problems(demo)).isEmpty();

		MockEnvironment withDev = deployed("demo");
		withDev.setActiveProfiles("dev", "demo");
		assertThat(DeploymentConfigurationGuard.problems(withDev)).isNotEmpty()
			.first()
			.asString()
			.contains("never accepted in a deployed environment");
	}

	// ---- refused: review R-4

	@Test
	void theDeveloperCommandLineOnAServerIsRefusedWithoutAnyDeclaration() {
		// Exactly what the review described: dev,demo, no APP_PUBLIC_BASE_URL, no APP_ENVIRONMENT, a database server.
		assertThat(DeploymentConfigurationGuard.problems(developer(SERVER_DB, "dev", "demo"))).singleElement()
			.asString()
			.contains("sql-uat.corp.example")
			.contains("only against a database on this machine");
		assertThat(DeploymentConfigurationGuard.problems(developer("jdbc:sqlserver://10.0.0.12\\PROD;databaseName=x",
				"local"))).hasSize(1);
	}

	@Test
	void aDevelopmentProfileIsRefusedInEveryDeployedEnvironmentWhateverTheAddress() {
		for (String environment : new String[] { "uat", "staging", "preprod", "production" }) {
			// Even with a local database and a plain-HTTP localhost address.
			MockEnvironment env = developer(LOCAL_DB, "dev").withProperty("app.environment", environment);
			assertThat(String.join("\n", DeploymentConfigurationGuard.problems(env))).as(environment)
				.contains("never accepted in a deployed environment");
		}
	}

	@Test
	void theDemoProfileIsRefusedInADeployedEnvironment() {
		for (String environment : new String[] { "uat", "staging", "preprod", "production" }) {
			MockEnvironment env = deployed(environment);
			env.setActiveProfiles("demo");
			assertThat(DeploymentConfigurationGuard.problems(env)).as(environment)
				.singleElement()
				.asString()
				.contains("demo profile")
				.contains("global administrator");
		}
	}

	@Test
	void aDeploymentMustDeclareItsEnvironmentAndUnknownNamesAreRefused() {
		MockEnvironment silent = deployed("");
		assertThat(DeploymentConfigurationGuard.problems(silent)).singleElement()
			.asString()
			.contains("APP_ENVIRONMENT is not set");
		for (String unknown : new String[] { "prod", "qa", "live", "prd" }) {
			assertThat(DeploymentConfigurationGuard.problems(deployed(unknown))).as(unknown)
				.singleElement()
				.asString()
				.contains("allowed values");
		}
		assertThat(DeploymentConfigurationGuard.problems(deployed("development"))).singleElement()
			.asString()
			.contains("needs the dev, local or test profile");
	}

	@Test
	void theDevelopmentProfileIsStillRefusedBehindAPublicHttpsAddress() {
		MockEnvironment env = developer(LOCAL_DB, "dev", "demo").withProperty("app.public-base-url",
				"https://uat.example.org");
		assertThat(DeploymentConfigurationGuard.problems(env)).singleElement()
			.asString()
			.contains("development profile")
			.contains("https://uat.example.org");
	}

	/** Review RR-6: a server with its own local database looks like a laptop, unless we look at who can reach it. */
	@Test
	void aDevelopmentInstanceMustListenOnThisMachineOnlyUnlessItSaysItIsDevelopment() {
		for (String address : new String[] { "", "0.0.0.0", "10.0.0.5", "uat-host" }) {
			MockEnvironment env = developer(LOCAL_DB, "dev", "demo").withProperty("server.address", address);
			assertThat(DeploymentConfigurationGuard.problems(env)).as(address)
				.singleElement()
				.asString()
				.contains("listens on this machine only");
		}
		assertThat(DeploymentConfigurationGuard.problems(developer(LOCAL_DB, "dev").withProperty("server.address", "localhost")))
			.isEmpty();
		assertThat(DeploymentConfigurationGuard.problems(developer(LOCAL_DB, "dev").withProperty("server.address", "0.0.0.0")
			.withProperty("app.environment", "development"))).isEmpty();
	}

	/** Review RR-9: the host after "//" is not the whole story. */
	@Test
	void aLocalLookingUrlThatConnectsElsewhereIsRefused() {
		for (String url : new String[] { "jdbc:sqlserver://localhost;serverName=sql-prod.corp;databaseName=x",
				"jdbc:sqlserver://localhost:1433;databaseName=x;ServerName = sql-prod.corp",
				"jdbc:sqlserver://localhost;databaseName=x;failoverPartner=sql-prod.corp" }) {
			assertThat(DeploymentConfigurationGuard.problems(developer(url, "dev"))).as(url)
				.singleElement()
				.asString()
				.contains("another host named in the connection properties");
		}
		assertThat(DeploymentConfigurationGuard.problems(developer(LOCAL_DB, "dev")
			.withProperty("spring.datasource.hikari.jdbc-url", SERVER_DB))).hasSize(1);
	}

	// ---- refused: deployed rules

	@Test
	void inADeployedEnvironmentEveryWeakSettingIsReportedAtOnce() {
		MockEnvironment env = new MockEnvironment().withProperty("app.environment", "uat")
			.withProperty("app.public-base-url", "http://uat.example.org")
			.withProperty("app.security.cookie-secure", "false")
			.withProperty("app.testing.adjustable-clock", "true")
			.withProperty("springdoc.swagger-ui.enabled", "true")
			.withProperty("spring.datasource.url", SERVER_DB);
		List<String> problems = DeploymentConfigurationGuard.problems(env);
		assertThat(problems).hasSize(7);
		assertThat(String.join("\n", problems)).contains("cookies must be secure")
			.contains("app.testing")
			.contains("springdoc")
			.contains("MAIL_MODE is not set")
			.contains("APP_PUBLIC_BASE_URL")
			.contains("APP_CLAMAV_HOST")
			.contains("MANAGEMENT_SERVER_PORT");
	}

	@Test
	void theContainerCookieSettingIsCheckedToo() {
		assertThat(DeploymentConfigurationGuard
			.problems(deployed("production").withProperty("server.servlet.session.cookie.secure", "false")))
			.singleElement()
			.asString()
			.contains("cookies must be secure");
	}

	@Test
	void anUnknownOrTestMailModeIsRefused() {
		assertThat(DeploymentConfigurationGuard.problems(deployed("uat").withProperty("app.mail.mode", "smpt")))
			.singleElement()
			.asString()
			.contains("'smpt'");
		assertThat(DeploymentConfigurationGuard.problems(deployed("uat").withProperty("app.mail.mode", "failing")))
			.singleElement()
			.asString()
			.contains("test hook");
		assertThat(DeploymentConfigurationGuard.problems(developer(LOCAL_DB, "dev").withProperty("app.mail.mode", "File")))
			.hasSize(1);
	}

	// ---- ADR-013, owner decision 2026-11-18: antivirus fails closed in deployed environments

	@Test
	void uatAndLaterEnvironmentsRefuseToStartWithoutAnAntivirusScanner() {
		for (String environment : new String[] { "uat", "staging", "preprod", "production" }) {
			MockEnvironment noScanner = deployed(environment).withProperty("app.attachments.clamav.host", " ");
			assertThat(DeploymentConfigurationGuard.problems(noScanner)).as(environment)
				.anyMatch(problem -> problem.contains("APP_CLAMAV_HOST"));
		}
	}

	@Test
	void noDeployedEnvironmentMayServeUnscannedFiles() {
		for (String environment : new String[] { "demo", "uat", "staging", "preprod", "production" }) {
			MockEnvironment unscanned = deployed(environment).withProperty("app.attachments.allow-unscanned-downloads",
					"true");
			if ("demo".equals(environment)) {
				unscanned.setActiveProfiles("demo");
			}
			assertThat(DeploymentConfigurationGuard.problems(unscanned)).as(environment)
				.anyMatch(problem -> problem.contains("APP_ALLOW_UNSCANNED_DOWNLOADS"));
		}
	}

	@Test
	void aDemonstrationServerMayRunWithoutAScannerItsUploadsStayInQuarantine() {
		MockEnvironment demo = deployed("demo").withProperty("app.attachments.clamav.host", "");
		demo.setActiveProfiles("demo");
		assertThat(DeploymentConfigurationGuard.problems(demo)).isEmpty();
	}

	// ---- ADR-018 monitoring: the management port listens on a private address only

	@Test
	void uatAndLaterEnvironmentsNeedAPrivateManagementPort() {
		assertThat(DeploymentConfigurationGuard.problems(deployed("uat").withProperty("management.server.port", "")))
			.anyMatch(problem -> problem.contains("MANAGEMENT_SERVER_PORT"));
		for (String address : new String[] { "0.0.0.0", "", "::", "203.0.113.7", "uat.example.org", "172.32.0.1" }) {
			assertThat(DeploymentConfigurationGuard
				.problems(deployed("production").withProperty("management.server.address", address))).as(address)
				.anyMatch(problem -> problem.contains("MANAGEMENT_SERVER_ADDRESS"));
		}
		for (String address : new String[] { "127.0.0.1", "10.1.2.3", "172.16.0.9", "192.168.1.20", "::1" }) {
			assertThat(DeploymentConfigurationGuard
				.problems(deployed("uat").withProperty("management.server.address", address))).as(address).isEmpty();
		}
	}

	@Test
	void theDatabaseHostIsReadWithoutInstanceOrPort() {
		assertThat(DeploymentConfigurationGuard.databaseHost("jdbc:sqlserver://Sql-01.corp:1433;databaseName=x"))
			.isEqualTo("sql-01.corp");
		assertThat(DeploymentConfigurationGuard.databaseHost("jdbc:sqlserver://sql-01\\INST;databaseName=x"))
			.isEqualTo("sql-01");
		assertThat(DeploymentConfigurationGuard.databaseHost("jdbc:sqlserver://;serverName=x")).isEmpty();
		assertThat(DeploymentConfigurationGuard.databaseHost("")).isEmpty();
	}

}
