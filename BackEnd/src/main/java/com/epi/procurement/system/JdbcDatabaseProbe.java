package com.epi.procurement.system;

import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Two cheap, time-limited queries per check: the server version and the latest applied schema version, read
 * straight from Flyway's history table (no Flyway scan per request - CR-07). Connection waits are bounded by the
 * pool's connection timeout.
 */
@Component
class JdbcDatabaseProbe implements DatabaseProbe {

	private static final Logger log = LoggerFactory.getLogger(JdbcDatabaseProbe.class);

	private static final int QUERY_TIMEOUT_SECONDS = 2;

	static final String SCHEMA_VERSION_SQL = """
			SELECT TOP (1) version FROM flyway_schema_history
			WHERE success = 1 AND version IS NOT NULL
			ORDER BY installed_rank DESC""";

	private final JdbcOperations jdbc;

	private final boolean exposeDetails;

	@Autowired
	JdbcDatabaseProbe(DataSource dataSource, @Value("${app.status.expose-details:false}") boolean exposeDetails) {
		this(timedTemplate(dataSource), exposeDetails);
	}

	JdbcDatabaseProbe(JdbcOperations jdbc, boolean exposeDetails) {
		this.jdbc = jdbc;
		this.exposeDetails = exposeDetails;
	}

	@Override
	public SystemStatus.DatabaseStatus probe() {
		try {
			String productVersion = jdbc.queryForObject(
					"SELECT CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128))", String.class);
			List<String> versions = jdbc.queryForList(SCHEMA_VERSION_SQL, String.class);
			String schemaVersion = versions.isEmpty() ? null : versions.get(0);
			return new SystemStatus.DatabaseStatus(SystemStatus.State.UP, exposeDetails ? productVersion : null,
					schemaVersion);
		}
		catch (RuntimeException ex) {
			log.warn("Database probe failed: {}", ex.getClass().getSimpleName());
			return SystemStatus.DatabaseStatus.down();
		}
	}

	private static JdbcTemplate timedTemplate(DataSource dataSource) {
		JdbcTemplate template = new JdbcTemplate(dataSource);
		template.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
		return template;
	}

}
