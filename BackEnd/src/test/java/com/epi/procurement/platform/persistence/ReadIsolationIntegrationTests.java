package com.epi.procurement.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.support.SqlServerTestSupport;

/**
 * ADR-014 (Sprint 2, P1.6): what a reader sees while another transaction holds an uncommitted write. With
 * READ_COMMITTED_SNAPSHOT off (every database people use today) the reader waits for the writer; with it on (only a
 * disposable test database, until the owner approves) it answers at once with the last committed value. The test
 * checks whichever mode the database is in, so it never needs to be skipped.
 */
class ReadIsolationIntegrationTests extends SqlServerTestSupport {

	@Autowired
	DataSource dataSource;

	@Test
	void aReaderWaitsForAWriterOrReadsTheLastCommittedValueUnderSnapshot() throws Exception {
		boolean snapshot = Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = DB_NAME()", Boolean.class));
		long companyId = companyId("DEMO-TN-PLT");
		String committed = jdbc.queryForObject("SELECT legal_name FROM company WHERE id = ?", String.class, companyId);

		try (Connection writer = dataSource.getConnection(); Connection reader = dataSource.getConnection()) {
			writer.setAutoCommit(false);
			try (PreparedStatement update = writer.prepareStatement("UPDATE company SET legal_name = ? WHERE id = ?")) {
				update.setString(1, committed + " (uncommitted)");
				update.setLong(2, companyId);
				update.executeUpdate();
			}
			try {
				try (Statement timeout = reader.createStatement()) {
					timeout.execute("SET LOCK_TIMEOUT 1000");
				}
				long started = System.nanoTime();
				if (snapshot) {
					assertThat(read(reader, companyId)).isEqualTo(committed);
					assertThat((System.nanoTime() - started) / 1_000_000).as("ms waited").isLessThan(1000);
				}
				else {
					assertThatThrownBy(() -> read(reader, companyId)).isInstanceOf(SQLException.class)
						.matches(ex -> ((SQLException) ex).getErrorCode() == SqlServerErrors.LOCK_TIMEOUT);
				}
			}
			finally {
				writer.rollback();
				try (Statement reset = reader.createStatement()) {
					reset.execute("SET LOCK_TIMEOUT -1");
				}
			}
		}
		assertThat(jdbc.queryForObject("SELECT legal_name FROM company WHERE id = ?", String.class, companyId))
			.isEqualTo(committed);
	}

	private static String read(Connection reader, long companyId) throws SQLException {
		try (PreparedStatement select = reader.prepareStatement("SELECT legal_name FROM company WHERE id = ?")) {
			select.setLong(1, companyId);
			try (ResultSet rs = select.executeQuery()) {
				rs.next();
				return rs.getString(1);
			}
		}
	}

}
