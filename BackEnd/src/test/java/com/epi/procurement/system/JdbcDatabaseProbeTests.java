package com.epi.procurement.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;

class JdbcDatabaseProbeTests {

	private final JdbcOperations jdbc = mock(JdbcOperations.class);

	private void databaseAnswers(List<String> schemaVersions) {
		given(jdbc.queryForObject(anyString(), eq(String.class))).willReturn("17.0.1000.7");
		given(jdbc.queryForList(JdbcDatabaseProbe.SCHEMA_VERSION_SQL, String.class)).willReturn(schemaVersions);
	}

	@Test
	void hidesTheProductVersionByDefault() {
		databaseAnswers(List.of("3"));

		SystemStatus.DatabaseStatus status = new JdbcDatabaseProbe(jdbc, false).probe();

		assertThat(status.status()).isEqualTo(SystemStatus.State.UP);
		assertThat(status.productVersion()).isNull();
		assertThat(status.schemaVersion()).isEqualTo("3");
	}

	@Test
	void exposesTheProductVersionWhenEnabled() {
		databaseAnswers(List.of("3"));

		assertThat(new JdbcDatabaseProbe(jdbc, true).probe().productVersion()).isEqualTo("17.0.1000.7");
	}

	@Test
	void reportsNoSchemaVersionWhenNothingIsMigrated() {
		databaseAnswers(List.of());

		SystemStatus.DatabaseStatus status = new JdbcDatabaseProbe(jdbc, true).probe();

		assertThat(status.status()).isEqualTo(SystemStatus.State.UP);
		assertThat(status.schemaVersion()).isNull();
	}

	@Test
	void mapsDatabaseFailuresToDownWithoutDetails() {
		given(jdbc.queryForObject(anyString(), eq(String.class)))
			.willThrow(new DataAccessResourceFailureException("Login failed for jdbc:sqlserver://secret"));

		SystemStatus.DatabaseStatus status = new JdbcDatabaseProbe(jdbc, true).probe();

		assertThat(status.status()).isEqualTo(SystemStatus.State.DOWN);
		assertThat(status.productVersion()).isNull();
		assertThat(status.schemaVersion()).isNull();
	}

}
