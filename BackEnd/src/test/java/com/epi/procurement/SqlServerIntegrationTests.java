package com.epi.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.audit.AuditChainVerification;
import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.organization.Company;
import com.epi.procurement.organization.CompanyRepository;
import com.epi.procurement.organization.CompanyType;
import com.epi.procurement.system.SystemStatus;
import com.epi.procurement.system.SystemStatusTestAccess;
import com.epi.procurement.platform.persistence.TransactionLocks;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Schema, reference data, audit trail and diagnostics against the real SQL Server test database.
 */
class SqlServerIntegrationTests extends SqlServerTestSupport {

	@Autowired
	Flyway flyway;

	@Autowired
	CompanyRepository companies;

	@Autowired
	AuditLog auditLog;

	@Autowired
	MockMvc mvc;

	@Autowired
	Environment environment;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void migratesFullyAndRerunIsANoOp() {
		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.migrate().migrationsExecuted).isZero();

		List<String> tables = jdbc.queryForList("SELECT name FROM sys.tables", String.class);
		assertThat(tables).contains("audit_chain_head", "audit_event", "company", "cost_center", "country",
				"currency", "department", "document_counter", "organization_group", "site", "user_account");
	}

	@Test
	void databaseSeededWithDemoDataValidatesWithoutTheDemoProfile() {
		// Same configuration as a start with "dev" only, after this database was migrated with "demo" (QA-D-02).
		Flyway withoutDemo = Flyway.configure()
			.configuration(flyway.getConfiguration())
			.locations("classpath:db/migration")
			.ignoreMigrationPatterns(environment.getProperty("spring.flyway.ignore-migration-patterns", String[].class))
			.load();

		assertThat(withoutDemo.validateWithResult().validationSuccessful).isTrue();
		assertThat(withoutDemo.migrate().migrationsExecuted).isZero();
		// Demo rows are not removed by switching the profile off.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE code LIKE 'DEMO-%'", Integer.class))
			.isEqualTo(4);
	}

	@Test
	void currenciesCarryIsoMinorUnits() {
		assertThat(jdbc.queryForObject("SELECT minor_units FROM currency WHERE code = 'TND'", Integer.class))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT minor_units FROM currency WHERE code = 'EUR'", Integer.class))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT minor_units FROM currency WHERE code = 'JPY'", Integer.class))
			.isZero();
	}

	@Test
	void demoDataIsLoadedAndLabelled() {
		List<Company> demo = companies.findAll().stream().filter(c -> c.getCode().startsWith("DEMO-")).toList();

		assertThat(demo).hasSize(4).allSatisfy(c -> assertThat(c.getLegalName()).endsWith("(Demo)"));
		assertThat(demo).extracting(Company::getType)
			.containsExactlyInAnyOrder(CompanyType.PARENT, CompanyType.PURCHASING_CENTER, CompanyType.SUBSIDIARY,
					CompanyType.SUBSIDIARY);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site WHERE name NOT LIKE N'%(Demo)'", Integer.class))
			.isZero();
	}

	@Test
	void instantsRoundTripAsUtcDatetime2() {
		jdbc.update("UPDATE company SET updated_at = '2026-01-15T10:30:45.123' WHERE code = 'DEMO-HOLD'");

		Company holding = companies.findAll().stream().filter(c -> c.getCode().equals("DEMO-HOLD")).findFirst().orElseThrow();

		assertThat(holding.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-15T10:30:45.123Z"));
		assertThat(jdbc.queryForObject(
				"SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'company' AND COLUMN_NAME = 'updated_at'",
				String.class))
			.isEqualTo("datetime2");
	}

	@Test
	void sameCompanyForeignKeysRejectCrossCompanyReferences() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO department (company_id, site_id, code, name)
				SELECT (SELECT id FROM company WHERE code = 'DEMO-FR-PLT'), (SELECT id FROM site WHERE code = 'DEMO-SFAX'),
				       'X-COMPANY', N'Cross-company test'"""))
			.hasMessageContaining("fk_department_site_same_company");
	}

	@Test
	void auditEventsAreChainedStoredInUtcAndAppendOnly() {
		Long companyId = companyId("DEMO-HOLD");
		long eventsBefore = auditLog.verifyChain("company:" + companyId).eventsChecked();
		Instant before = Instant.now();
		long first = auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "1", "CREATE", null,
				"DRAFT", "first", "{\"n\":1}"));
		long second = auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "1", "SUBMIT",
				"DRAFT", "SUBMITTED", "Spécification validée", null));

		assertThat(second).isGreaterThan(first);
		AuditChainVerification verification = auditLog.verifyChain("company:" + companyId);
		assertThat(verification.valid()).isTrue();
		assertThat(verification.eventsChecked()).isEqualTo(eventsBefore + 2);

		Instant stored = jdbc.queryForObject("SELECT occurred_at FROM audit_event WHERE id = ?", LocalDateTime.class, first)
			.toInstant(ZoneOffset.UTC);
		assertThat(Duration.between(before, stored).abs()).isLessThan(Duration.ofMinutes(1));

		assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET comment = 'tampered' WHERE id = ?", first))
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", first))
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.execute("TRUNCATE TABLE audit_event")).hasMessageContaining("FOREIGN KEY");
		assertThat(auditLog.verifyChain("company:" + companyId).valid()).isTrue();
	}

	@Test
	void chainHeadsCannotBeDeletedRewoundOrRekeyed() {
		Long companyId = companyId("DEMO-TN-PLT");
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "3", "CREATE", null, null, null, null));
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "3", "SUBMIT", null, null, null, null));
		String chainKey = "company:" + companyId;
		long lastSeq = jdbc.queryForObject("SELECT last_seq FROM audit_chain_head WHERE chain_key = ?", Long.class,
				chainKey);

		assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_chain_head WHERE chain_key = ?", chainKey))
			.hasMessageContaining("cannot be deleted");
		assertThatThrownBy(() -> jdbc.update("UPDATE audit_chain_head SET last_seq = 1 WHERE chain_key = ?", chainKey))
			.hasMessageContaining("strictly increase");
		// Re-keying is refused by the events' foreign key, or by the trigger for a head without events.
		assertThatThrownBy(() -> jdbc.update("UPDATE audit_chain_head SET chain_key = 'company:0' WHERE chain_key = ?",
				chainKey))
			.isInstanceOf(DataAccessException.class);
		jdbc.update("INSERT INTO audit_chain_head (chain_key, last_seq, last_hash) VALUES ('empty-chain', 0, NULL)");
		assertThatThrownBy(() -> jdbc.update(
				"UPDATE audit_chain_head SET chain_key = 'renamed-chain' WHERE chain_key = 'empty-chain'"))
			.hasMessageContaining("cannot be changed");
		assertThatThrownBy(() -> jdbc.update("UPDATE audit_chain_head SET last_seq = 0 WHERE chain_key = 'empty-chain'"))
			.hasMessageContaining("strictly increase");

		assertThat(jdbc.queryForObject("SELECT last_seq FROM audit_chain_head WHERE chain_key = ?", Long.class, chainKey))
			.isEqualTo(lastSeq);
		assertThat(auditLog.verifyChain(chainKey).valid()).isTrue();
	}

	@Test
	void anAppendRolledBackWithItsBusinessTransactionLeavesNoTrace() {
		Long companyId = companyId("DEMO-TN-PLT");
		String chainKey = "company:" + companyId;
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "4", "CREATE", null, null, null, null));
		long headBefore = jdbc.queryForObject("SELECT last_seq FROM audit_chain_head WHERE chain_key = ?", Long.class,
				chainKey);

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "4", "APPROVE", null, null,
					null, null));
			throw new IllegalStateException("business change failed");
		})).hasMessage("business change failed");

		assertThat(jdbc.queryForObject("SELECT last_seq FROM audit_chain_head WHERE chain_key = ?", Long.class, chainKey))
			.isEqualTo(headBefore);
		assertThat(auditLog.verifyChain(chainKey).valid()).isTrue();
	}

	@Test
	void aChainWithoutHeadIsReportedInvalid() {
		AuditChainVerification verification = auditLog.verifyChain("company:999999999");

		assertThat(verification.valid()).isFalse();
		assertThat(verification.eventsChecked()).isZero();
	}

	@Test
	void verificationDuringConcurrentAppendsNeverRaisesAFalseAlarm() throws Exception {
		// Uses the global chain (no company) so the other tests' per-company counts are unaffected.
		Long companyId = null;
		String chainKey = "global";
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "5", "CREATE", null, null, null, null));
		ExecutorService writer = Executors.newSingleThreadExecutor();
		try {
			Future<?> appends = writer.submit(() -> {
				for (int i = 0; i < 60; i++) {
					auditLog.record(new AuditRecord(null, "Writer", companyId, "TEST", "5", "PING", null, null, null, null));
				}
			});
			int verifications = 0;
			while (!appends.isDone()) {
				assertThat(auditLog.verifyChain(chainKey).valid()).isTrue();
				verifications++;
			}
			appends.get();
			assertThat(verifications).isPositive();
		}
		finally {
			writer.shutdown();
		}
		assertThat(auditLog.verifyChain(chainKey).valid()).isTrue();
	}

	@Test
	void tamperingIsDetectedByChainVerification() {
		Long companyId = companyId("DEMO-FR-PLT");
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "2", "CREATE", null, "DRAFT", null, null));
		long target = auditLog.record(
				new AuditRecord(null, "Integration test", companyId, "TEST", "2", "APPROVE", "DRAFT", "APPROVED", "ok", null));
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "2", "CLOSE", "APPROVED", "CLOSED", null, null));

		jdbc.execute("DISABLE TRIGGER trg_audit_event_append_only ON audit_event");
		try {
			jdbc.update("UPDATE audit_event SET comment = 'changed' WHERE id = ?", target);
		}
		finally {
			jdbc.execute("ENABLE TRIGGER trg_audit_event_append_only ON audit_event");
		}

		try {
			AuditChainVerification verification = auditLog.verifyChain("company:" + companyId);
			assertThat(verification.valid()).isFalse();
			assertThat(verification.firstInvalidEventId()).isEqualTo(target);
		}
		finally {
			// The database is shared by all integration tests: restore the original content.
			jdbc.execute("DISABLE TRIGGER trg_audit_event_append_only ON audit_event");
			try {
				jdbc.update("UPDATE audit_event SET comment = 'ok' WHERE id = ?", target);
			}
			finally {
				jdbc.execute("ENABLE TRIGGER trg_audit_event_append_only ON audit_event");
			}
		}
		assertThat(auditLog.verifyChain("company:" + companyId).valid()).isTrue();
	}

	@Test
	void concurrentAppendsProduceOneLinearChain() throws Exception {
		Long companyId = companyId("DEMO-CPA");
		long eventsBefore = auditLog.verifyChain("company:" + companyId).eventsChecked();
		int threads = 8;
		int perThread = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				int thread = t;
				futures.add(pool.submit(() -> {
					for (int i = 0; i < perThread; i++) {
						auditLog.record(new AuditRecord(null, "Thread " + thread, companyId, "TEST", "concurrency",
								"PING", null, null, "event " + i, null));
					}
				}));
			}
			for (Future<?> future : futures) {
				future.get();
			}
		}
		finally {
			pool.shutdown();
		}

		AuditChainVerification verification = auditLog.verifyChain("company:" + companyId);
		assertThat(verification.valid()).isTrue();
		assertThat(verification.eventsChecked()).isEqualTo(eventsBefore + threads * perThread);
	}

	@Test
	void statusEndpointReportsTheRealDatabaseToAdministrators() throws Exception {
		mvc.perform(get("/api/v1/system/status").with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.database.status").value("UP"))
			.andExpect(jsonPath("$.database.schemaVersion").value(flyway.info().current().getVersion().getVersion()))
			.andExpect(jsonPath("$.database.productVersion").isNotEmpty());
	}

	@Test
	void probeHidesServerDetailsWhenNotEnabled() {
		SystemStatus.DatabaseStatus status = SystemStatusTestAccess.probe(jdbc, false);

		assertThat(status.status()).isEqualTo(SystemStatus.State.UP);
		assertThat(status.productVersion()).isNull();
		assertThat(status.schemaVersion()).isEqualTo(flyway.info().current().getVersion().getVersion());
	}

	@Test
	void actuatorHealthShowsTheDatabaseToAdministratorsOnly() throws Exception {
		mvc.perform(get("/actuator/health").with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.db.status").value("UP"));
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
	}

	/**
	 * QA1-D-06: appending still works when a trigger exists on audit_event (e.g. one added by a DBA).
	 */
	@Test
	void auditAppendsWorkWithATriggerOnTheTable() {
		jdbc.execute("CREATE TRIGGER trg_test_audit_noop ON audit_event AFTER INSERT AS SET NOCOUNT ON;");
		try {
			long id = auditLog.record(new AuditRecord(null, "Integration test", null, "TEST", "7", "PING", null, null,
					null, null));
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE id = ?", Integer.class, id))
				.isEqualTo(1);
		}
		finally {
			jdbc.execute("DROP TRIGGER trg_test_audit_noop");
		}
		assertThat(auditLog.verifyChain("global").valid()).isTrue();
	}

	/**
	 * S1CR-03: an application lock held elsewhere makes the caller fail fast with 503 busy, not hang.
	 */
	@Test
	void aHeldApplicationLockFailsFastAsBusy(@Autowired TransactionLocks locks) throws Exception {
		java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		ExecutorService holder = Executors.newSingleThreadExecutor();
		try {
			Future<?> holding = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
				locks.lockExclusively("test-resource");
				held.countDown();
				try {
					release.await();
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}));
			if (!held.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
				// Surface the holder's failure instead of waiting forever.
				holding.get(1, java.util.concurrent.TimeUnit.SECONDS);
				throw new AssertionError("The holder never acquired the application lock");
			}
			long start = System.nanoTime();
			assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
				.executeWithoutResult(tx -> locks.lockExclusively("test-resource")))
				.isInstanceOfSatisfying(ApiProblemException.class, ex -> {
					assertThat(ex.getStatus().value()).isEqualTo(503);
					assertThat(ex.getCode()).isEqualTo("busy");
				});
			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
			release.countDown();
			holding.get();
		}
		finally {
			release.countDown();
			holder.shutdown();
		}
	}

	/**
	 * S1CR-09: after commit and after rollback the application lock is released and no transaction stays open on
	 * the pooled connection.
	 */
	@Test
	void applicationLocksDoNotOutliveTheirTransaction(@Autowired TransactionLocks locks) {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.executeWithoutResult(status -> locks.lockExclusively("s1cr09-resource"));
		tx.executeWithoutResult(status -> {
			locks.lockExclusively("s1cr09-resource");
			status.setRollbackOnly();
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.dm_tran_locks WHERE resource_type = 'APPLICATION' "
				+ "AND resource_description LIKE '%s1cr09%'", Integer.class)).isZero();
		for (int i = 0; i < 5; i++) {
			assertThat(jdbc.queryForObject("SELECT @@TRANCOUNT", Integer.class)).isZero();
		}
		// A third transaction gets the lock immediately.
		tx.executeWithoutResult(status -> locks.lockExclusively("s1cr09-resource"));
	}

	/**
	 * CR-25: an event positioned after the chain head (head rewound by someone able to bypass the triggers) makes
	 * the chain invalid.
	 */
	@Test
	void eventsBeyondTheChainHeadAreReported() {
		Long companyId = companyId("DEMO-HOLD");
		String chainKey = "company:" + companyId;
		auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "6", "CREATE", null, null, null, null));
		long hidden = auditLog.record(new AuditRecord(null, "Integration test", companyId, "TEST", "6", "SUBMIT", null,
				null, null, null));
		long hiddenSeq = jdbc.queryForObject("SELECT chain_seq FROM audit_event WHERE id = ?", Long.class, hidden);
		Long previous = jdbc.queryForObject("SELECT id FROM audit_event WHERE chain_key = ? AND chain_seq = ?",
				Long.class, chainKey, hiddenSeq - 1);
		byte[] previousHash = jdbc.queryForObject("SELECT hash FROM audit_event WHERE id = ?", byte[].class, previous);

		jdbc.execute("DISABLE TRIGGER trg_audit_chain_head_forward_only ON audit_chain_head");
		try {
			jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ?, last_event_id = ? WHERE chain_key = ?",
					hiddenSeq - 1, previousHash, previous, chainKey);
			AuditChainVerification verification = auditLog.verifyChain(chainKey);
			assertThat(verification.valid()).isFalse();
			assertThat(verification.firstInvalidEventId()).isEqualTo(hidden);
		}
		finally {
			byte[] hiddenHash = jdbc.queryForObject("SELECT hash FROM audit_event WHERE id = ?", byte[].class, hidden);
			jdbc.update("UPDATE audit_chain_head SET last_seq = ?, last_hash = ?, last_event_id = ? WHERE chain_key = ?",
					hiddenSeq, hiddenHash, hidden, chainKey);
			jdbc.execute("ENABLE TRIGGER trg_audit_chain_head_forward_only ON audit_chain_head");
		}
		assertThat(auditLog.verifyChain(chainKey).valid()).isTrue();
	}

}
