package com.epi.procurement.system;

import org.springframework.jdbc.core.JdbcOperations;

/**
 * Lets tests outside this package exercise the package-private probe against a real database.
 */
public final class SystemStatusTestAccess {

	private SystemStatusTestAccess() {
	}

	public static SystemStatus.DatabaseStatus probe(JdbcOperations jdbc, boolean includeDetails) {
		return new JdbcDatabaseProbe(jdbc).probe(includeDetails);
	}

}
