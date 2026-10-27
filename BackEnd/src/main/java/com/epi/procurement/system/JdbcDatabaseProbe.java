package com.epi.procurement.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Two cheap, time-limited queries per check: the server version and the latest applied schema version, read
 * straight from Flyway's history table (no Flyway scan per request - CR-07).
 * <p>
 * The whole check is bounded by its own deadline (CR-26), so the status page answers DOWN quickly when the
 * database is unreachable without shortening the connection pool's timeout for ordinary requests.
 */
@Component
class JdbcDatabaseProbe implements DatabaseProbe, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(JdbcDatabaseProbe.class);

	private static final int QUERY_TIMEOUT_SECONDS = 2;

	static final long DEADLINE_MILLIS = 1500;

	static final String SCHEMA_VERSION_SQL = """
			SELECT TOP (1) version FROM flyway_schema_history
			WHERE success = 1 AND version IS NOT NULL
			ORDER BY installed_rank DESC""";

	private final JdbcOperations jdbc;

	private final ExecutorService executor;

	@Autowired
	JdbcDatabaseProbe(DataSource dataSource) {
		this.jdbc = timedTemplate(dataSource);
		this.executor = Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "database-probe");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Probes synchronously, without the deadline (tests).
	 */
	JdbcDatabaseProbe(JdbcOperations jdbc) {
		this.jdbc = jdbc;
		this.executor = null;
	}

	@Override
	public SystemStatus.DatabaseStatus probe(boolean includeDetails) {
		if (executor == null) {
			return query(includeDetails);
		}
		CompletableFuture<SystemStatus.DatabaseStatus> check = CompletableFuture.supplyAsync(() -> query(includeDetails),
				executor);
		try {
			return check.get(DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			check.cancel(true);
			log.warn("Database probe exceeded {} ms", DEADLINE_MILLIS);
			return SystemStatus.DatabaseStatus.down();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return SystemStatus.DatabaseStatus.down();
		}
		catch (ExecutionException ex) {
			return SystemStatus.DatabaseStatus.down();
		}
	}

	@Override
	public void destroy() {
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	private SystemStatus.DatabaseStatus query(boolean includeDetails) {
		try {
			String productVersion = jdbc.queryForObject(
					"SELECT CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(128))", String.class);
			List<String> versions = jdbc.queryForList(SCHEMA_VERSION_SQL, String.class);
			String schemaVersion = versions.isEmpty() ? null : versions.get(0);
			return new SystemStatus.DatabaseStatus(SystemStatus.State.UP, includeDetails ? productVersion : null,
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
