package com.epi.procurement.logistics;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epi.procurement.platform.persistence.TransactionLocks;

/** Shipment numbers {@code {COMPANY}-SH-{YYYY}-{seq6}}. */
@Component
class ShipmentNumbering {

	static final String KIND = "SHIPMENT";

	private final JdbcTemplate jdbc;

	private final TransactionLocks locks;

	ShipmentNumbering(DataSource dataSource, TransactionLocks locks) {
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
		return "%s-SH-%d-%06d".formatted(companyCode, year, value);
	}

	private Long increment(long companyId, int year) {
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
