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

	@Test
	void error1222IsALockTimeoutHoweverItIsWrapped() {
		SQLException timeout = new SQLException("Lock request time out period exceeded.", "S00051", 1222);
		assertThat(SqlServerErrors.isLockTimeout(timeout)).isTrue();
		assertThat(SqlServerErrors.isLockTimeout(new IllegalStateException("outer", new RuntimeException(timeout))))
			.isTrue();
		SQLException first = new SQLException("statement", "S0001", 3621);
		first.setNextException(timeout);
		assertThat(SqlServerErrors.isLockTimeout(first)).isTrue();
		assertThat(SqlServerErrors.isLockTimeout(new SQLException("deadlock", "40001", 1205))).isFalse();
		assertThat(SqlServerErrors.isLockTimeout(new IllegalStateException("no sql"))).isFalse();
	}

}
