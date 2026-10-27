package com.epi.procurement.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class SqlServerErrorsTests {

	@Test
	void onlyError1205IsADeadlockVictim() {
		assertThat(SqlServerErrors.isDeadlockVictim(
				new CannotAcquireLockException("wrapped", new SQLException("deadlock", "40001", 1205)))).isTrue();
		// Lock request timeout (1222) and application-lock timeouts must not be retried.
		assertThat(SqlServerErrors.isDeadlockVictim(
				new CannotAcquireLockException("wrapped", new SQLException("timeout", "HYT00", 1222)))).isFalse();
		assertThat(SqlServerErrors.isDeadlockVictim(new CannotAcquireLockException("no cause"))).isFalse();
	}

}
