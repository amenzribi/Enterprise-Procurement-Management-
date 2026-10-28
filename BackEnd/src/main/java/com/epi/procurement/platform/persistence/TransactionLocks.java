package com.epi.procurement.platform.persistence;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.web.ApiProblemException;

/**
 * Named exclusive locks held until the current transaction ends (SQL Server {@code sp_getapplock}).
 * <p>
 * Used to create a missing singleton row (a company's first audit chain head, the first document counter of a
 * year) without key-range locks: locking a row that does not exist yet with UPDLOCK + HOLDLOCK takes range locks on
 * which two first writers can deadlock, and creating the row in a separate transaction needs a second pooled
 * connection per writer, which exhausts the pool under load. The application lock stays on the caller's connection
 * and only serialises writers of the same resource.
 * <p>
 * The wait is short: if the lock is not obtained in time the request fails fast with 503 {@code busy} (retryable)
 * rather than hanging (S1CR-03).
 */
@Component
public class TransactionLocks {

	static final int TIMEOUT_MILLIS = 2_000;

	private final JdbcTemplate jdbc;

	TransactionLocks(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockExclusively(String resource) {
		// S1CR-09: the explicit BEGIN below is only safe on the connection bound to the Spring transaction; on an
		// auto-commit connection it would leave an open transaction (and its lock) in the pool.
		Boolean autoCommit = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> c.getAutoCommit());
		if (Boolean.TRUE.equals(autoCommit)) {
			throw new IllegalStateException("Application locks require a transaction-bound connection");
		}
		// With auto-commit off the driver uses implicit transactions, which only start at the first statement touching
		// a table; sp_getapplock does not, so as the first statement it would run with @@TRANCOUNT = 0 and fail (-999).
		// Opening the transaction explicitly is safe: the driver's commit/rollback act while @@TRANCOUNT > 0.
		Integer result = jdbc.queryForObject("""
				IF @@TRANCOUNT = 0 BEGIN TRANSACTION;
				DECLARE @result INT;
				EXEC @result = sp_getapplock @Resource = ?, @LockMode = 'Exclusive', @LockOwner = 'Transaction',
				                             @LockTimeout = ?;
				SELECT @result;""", Integer.class, resource, TIMEOUT_MILLIS);
		if (result == null || result < 0) {
			throw busy();
		}
	}

	/**
	 * Releases the lock before the transaction ends, once the protected singleton row exists: the new row's own
	 * lock then serialises the other writers, so first-of-year submissions no longer queue on the application lock
	 * for the whole transaction (Sprint 2: submissions also build the approval circuit).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void release(String resource) {
		jdbc.queryForObject("""
				DECLARE @result INT;
				EXEC @result = sp_releaseapplock @Resource = ?, @LockOwner = 'Transaction';
				SELECT @result;""", Integer.class, resource);
	}

	/**
	 * 503 {@code busy}: a concurrent operation holds a lock this one needs; the client may retry shortly.
	 */
	public static ApiProblemException busy() {
		return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "busy",
				"The system is busy with a concurrent operation. Please try again in a moment.");
	}

}
