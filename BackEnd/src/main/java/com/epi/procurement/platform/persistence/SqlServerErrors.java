package com.epi.procurement.platform.persistence;

import java.sql.SQLException;

/**
 * Classification of SQL Server errors by native error number, independent of how JDBC, Hibernate or Spring wrapped
 * them.
 */
public final class SqlServerErrors {

	/** "Transaction was deadlocked ... and has been chosen as the deadlock victim. Rerun the transaction." */
	public static final int DEADLOCK_VICTIM = 1205;

	private SqlServerErrors() {
	}

	/**
	 * Only a deadlock victim is safe and useful to retry in a new transaction; lock timeouts are not (S1CR-03).
	 */
	public static boolean isDeadlockVictim(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql) {
				for (SQLException chained = sql; chained != null; chained = chained.getNextException()) {
					if (chained.getErrorCode() == DEADLOCK_VICTIM) {
						return true;
					}
				}
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return false;
	}

}
