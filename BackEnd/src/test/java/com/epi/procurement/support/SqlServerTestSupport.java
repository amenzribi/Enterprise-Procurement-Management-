package com.epi.procurement.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class of every test that needs the real SQL Server database (never a substitute). Enabled with
 * {@code mvnw verify -Pdb-it}; reported as skipped otherwise.
 * <p>
 * All subclasses share one Spring context (identical configuration), so the test database is cleaned and
 * re-migrated once per run and the demo users are seeded once. Tests therefore create their own data and must
 * not assume an empty database. The database name must end in {@code _test}.
 */
@RequiresSqlServer
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "demo" })
@Import(SqlServerTestSupport.TestDatabase.class)
public abstract class SqlServerTestSupport {

	/** Password of every demo account in the test profile (application-test.yml). */
	public static final String DEMO_PASSWORD = "Test-Only-Demo-Password-1";

	@LocalServerPort
	protected int port;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	protected ApiClient client() {
		return new ApiClient(port);
	}

	protected ApiClient signedIn(String email) {
		ApiClient client = client();
		client.login(email, DEMO_PASSWORD).expectStatus(200);
		return client;
	}

	protected long userId(String email) {
		return jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
	}

	protected long companyId(String code) {
		return jdbc.queryForObject("SELECT id FROM company WHERE code = ?", Long.class, code);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class TestDatabase {

		@Bean
		MutableClock testClock() {
			return new MutableClock();
		}

		@Bean
		FlywayMigrationStrategy cleanThenMigrate() {
			return flyway -> {
				String database = new JdbcTemplate(flyway.getConfiguration().getDataSource())
					.queryForObject("SELECT DB_NAME()", String.class);
				if (database == null || !database.endsWith("_test")) {
					throw new IllegalStateException("Refusing to clean non-test database " + database + " ("
							+ flyway.getConfiguration().getUrl() + ")");
				}
				flyway.clean();
				flyway.migrate();
			};
		}

	}

}
