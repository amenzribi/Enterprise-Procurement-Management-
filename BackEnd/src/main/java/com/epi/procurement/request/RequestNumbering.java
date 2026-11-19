package com.epi.procurement.request;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.persistence.TransactionLocks;

/**
 * Request numbers {@code {COMPANY_CODE}-PR-{YYYY}-{seq6}} from {@code document_counter}, inside the submitting
 * transaction. Numbers are gap-free per company and year, because (1) the increment commits or rolls back with the
 * submission (a rolled-back number is issued again: FirstWriterConcurrencyIntegrationTests), (2) concurrent
 * submissions are serialised on the counter row, also under READ_COMMITTED_SNAPSHOT (the UPDATE locks), and (3) a
 * numbered request is never deleted: only drafts are discarded, a request never returns to draft, and the
 * administrators' draft deletion refuses a numbered request ("has-number"). Beyond 999 999 the sequence widens to more
 * digits (no truncation).
 * <p>
 * Concurrency (S1DR-10): the increment locks the counter row until the submitting transaction commits, which
 * serialises submissions of one company and year. A missing row (first submission of a year) is created under an
 * application lock in the same transaction (see {@link TransactionLocks}), avoiding key-range lock deadlocks.
 */
@Component
class RequestNumbering {

	static final String KIND = "PURCHASE_REQUEST";

	private final JdbcTemplate jdbc;

	private final TransactionLocks locks;

	RequestNumbering(DataSource dataSource, TransactionLocks locks) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.locks = locks;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	String next(long companyId, String companyCode, int year) {
		Long value = increment(companyId, year);
		// First number of a year: create the row, then increment it. The increment waits for a first writer that has
		// not committed yet; if that writer rolled back, the row is gone and is created again (bounded).
		for (int attempt = 1; value == null; attempt++) {
			if (attempt > 3) {
				throw new IllegalStateException("Document counter could not be created for company " + companyId);
			}
			createCounterIfMissing(companyId, year);
			value = increment(companyId, year);
		}
		return format(companyCode, year, value);
	}

	static String format(String companyCode, int year, long sequence) {
		return "%s-PR-%d-%06d".formatted(companyCode, year, sequence);
	}

	private Long increment(long companyId, int year) {
		// OUTPUT ... INTO a table variable: a plain OUTPUT clause is rejected once a trigger exists on the table.
		// (No SET NOCOUNT: it would persist on the pooled connection and hide update counts from Hibernate.)
		return jdbc.query("""
				DECLARE @issued TABLE (value BIGINT NOT NULL);
				UPDATE document_counter WITH (ROWLOCK) SET next_value = next_value + 1
				OUTPUT DELETED.next_value INTO @issued
				WHERE company_id = ? AND year = ? AND kind = ?;
				SELECT value FROM @issued;""", rs -> rs.next() ? rs.getLong(1) : null, companyId, year, KIND);
	}

	private void createCounterIfMissing(long companyId, int year) {
		String resource = "document-counter:" + companyId + ":" + year + ":" + KIND;
		locks.lockExclusively(resource);
		// The existence check reads uncommitted rows (Sprint 2 stabilisation): a first writer's new row, not yet
		// committed, is seen, so the next writer neither waits for it while holding the application lock (which made
		// the writers behind it answer 503) nor, under READ_COMMITTED_SNAPSHOT, misses it and inserts a duplicate. It
		// waits on the row itself in increment(), like every later writer. Inserters are serialised by the lock.
		jdbc.update("INSERT INTO document_counter (company_id, year, kind, next_value) SELECT ?, ?, ?, 1 "
				+ "WHERE NOT EXISTS (SELECT 1 FROM document_counter WITH (READUNCOMMITTED) "
				+ "WHERE company_id = ? AND year = ? AND kind = ?)",
				companyId, year, KIND, companyId, year, KIND);
		locks.release(resource);
	}

}
