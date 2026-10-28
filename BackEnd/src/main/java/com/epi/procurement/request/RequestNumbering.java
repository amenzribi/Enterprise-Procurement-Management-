package com.epi.procurement.request;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.persistence.TransactionLocks;

/**
 * Request numbers {@code {COMPANY_CODE}-PR-{YYYY}-{seq6}} from {@code document_counter}, inside the submitting
 * transaction. Numbers are gap-free per company and year: a rolled-back submission also rolls back its increment,
 * and submitted requests are never deleted. Beyond 999 999 the sequence widens to more digits (no truncation).
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
		if (value == null) {
			createCounterIfMissing(companyId, year);
			value = increment(companyId, year);
			if (value == null) {
				throw new IllegalStateException("Document counter could not be created for company " + companyId);
			}
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
		jdbc.update("INSERT INTO document_counter (company_id, year, kind, next_value) SELECT ?, ?, ?, 1 "
				+ "WHERE NOT EXISTS (SELECT 1 FROM document_counter WHERE company_id = ? AND year = ? AND kind = ?)",
				companyId, year, KIND, companyId, year, KIND);
		locks.release(resource);
	}

}
