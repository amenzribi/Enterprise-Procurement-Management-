package com.epi.procurement.platform.persistence;

import java.sql.SQLException;

/**
 * Classification of SQL Server errors by native error number, independent of how JDBC, Hibernate or Spring wrapped
 * them.
 */
public final class SqlServerErrors {

	/** "Transaction was deadlocked ... and has been chosen as the deadlock victim. Rerun the transaction." */
	public static final int DEADLOCK_VICTIM = 1205;

	/** "Lock request time out period exceeded." Raised when a statement waited longer than SET LOCK_TIMEOUT. */
	public static final int LOCK_TIMEOUT = 1222;

	private SqlServerErrors() {
	}

	/** The statement gave up waiting for a lock held by another transaction (audit DB-3). */
	public static boolean isLockTimeout(Throwable error) {
		return hasErrorCode(error, LOCK_TIMEOUT);
	}

	/**
	 * Only a deadlock victim is safe and useful to retry in a new transaction; lock timeouts are not (S1CR-03).
	 */
	public static boolean isDeadlockVictim(Throwable error) {
		return hasErrorCode(error, DEADLOCK_VICTIM);
	}

	/**
	 * The first SQL Server native error number in the cause chain, or 0 when there is none. For diagnostics: it names
	 * the failure (1205 deadlock victim, 1222 lock timeout) without exposing statements or parameter values.
	 */
	public static int errorCode(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getErrorCode() != 0) {
				return sql.getErrorCode();
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return 0;
	}

	private static boolean hasErrorCode(Throwable error, int code) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql) {
				for (SQLException chained = sql; chained != null; chained = chained.getNextException()) {
					if (chained.getErrorCode() == code) {
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
