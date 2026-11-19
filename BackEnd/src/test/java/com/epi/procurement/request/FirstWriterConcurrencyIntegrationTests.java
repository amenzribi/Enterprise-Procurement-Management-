package com.epi.procurement.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Sprint 2 stabilisation: the first writers of a new singleton row (a year's document counter, a company's audit
 * chain head). They must all succeed, never answer 503 because one of them holds a lock while waiting, never create
 * the row twice (also under READ_COMMITTED_SNAPSHOT), and numbers must stay unique and contiguous, rollbacks
 * included.
 */
class FirstWriterConcurrencyIntegrationTests extends SqlServerTestSupport {

	/**
	 * How long each first writer keeps its transaction open after taking its row: longer than the 2 s application-lock
	 * wait, so a writer that waits while holding that lock makes the next one fail; three writers in a row stay inside
	 * the 5 s lock timeout of the test profile.
	 */
	private static final long HOLD_MILLIS = 2_100;

	private static final int FIRST_WRITERS = 3;

	@Autowired
	RequestNumbering numbering;

	@Autowired
	AuditLog auditLog;

	@Autowired
	PlatformTransactionManager transactions;

	private static int freshYear() {
		// ck_document_counter_year: 2000 to 2999; far from real years.
		return 2200 + ThreadLocalRandom.current().nextInt(0, 800);
	}

	private long companyId() {
		return companyId("DEMO-TN-PLT");
	}

	private long sequence(String number) {
		return Long.parseLong(number.substring(number.lastIndexOf('-') + 1));
	}

	private <T> List<T> concurrently(int writers, Callable<T> work) throws Exception {
		CyclicBarrier start = new CyclicBarrier(writers);
		ExecutorService pool = Executors.newFixedThreadPool(writers);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < writers; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return work.call();
				}));
			}
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void theFirstNumbersOfAYearAreTakenConcurrentlyWithoutBusyOrDuplicates() throws Exception {
		int year = freshYear();
		TransactionTemplate tx = new TransactionTemplate(transactions);
		List<String> numbers = concurrently(FIRST_WRITERS, () -> tx.execute(status -> {
			String number = numbering.next(companyId(), "DEMO-TN-PLT", year);
			sleep(HOLD_MILLIS);
			return number;
		}));
		assertThat(numbers.stream().map(this::sequence).sorted().toList()).containsExactly(1L, 2L, 3L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_counter WHERE company_id = ? AND year = ? AND kind = ?",
				Integer.class, companyId(), year, RequestNumbering.KIND)).isEqualTo(1);
	}

	@Test
	void aRolledBackNumberIsReusedSoCommittedNumbersStayContiguous() throws Exception {
		int year = freshYear();
		TransactionTemplate tx = new TransactionTemplate(transactions);
		String first = tx.execute(status -> {
			String number = numbering.next(companyId(), "DEMO-TN-PLT", year);
			status.setRollbackOnly();
			return number;
		});
		String reused = tx.execute(status -> numbering.next(companyId(), "DEMO-TN-PLT", year));
		assertThat(sequence(first)).isEqualTo(1L);
		assertThat(reused).isEqualTo(first);

		// Concurrently, some of eight writers roll back after taking a number: those that commit hold 2, 3, 4 and so on.
		List<Long> committed = concurrently(8, () -> {
			boolean rollback = ThreadLocalRandom.current().nextBoolean();
			return tx.execute(status -> {
				String number = numbering.next(companyId(), "DEMO-TN-PLT", year);
				sleep(200);
				if (rollback) {
					status.setRollbackOnly();
					return null;
				}
				return sequence(number);
			});
		}).stream().filter(java.util.Objects::nonNull).sorted().toList();
		List<Long> expected = new ArrayList<>();
		for (long n = 2; n < 2 + committed.size(); n++) {
			expected.add(n);
		}
		assertThat(committed).isEqualTo(expected);
		assertThat(jdbc.queryForObject("SELECT next_value FROM document_counter WHERE company_id = ? AND year = ? AND kind = ?",
				Long.class, companyId(), year, RequestNumbering.KIND)).isEqualTo(2L + committed.size());
	}

	@Test
	void theFirstEventsOfANewAuditChainAreAppendedConcurrentlyWithoutBusyOrForks() throws Exception {
		String code = "FW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		jdbc.update("""
				INSERT INTO company (group_id, code, legal_name, company_type, country_code, currency_code, default_language,
				                     time_zone, purchasing_mode, is_active)
				SELECT group_id, ?, ?, company_type, country_code, currency_code, default_language, time_zone, purchasing_mode, 0
				FROM company WHERE code = 'DEMO-TN-PLT'""", code, "First writer probe (Demo)");
		long probe = companyId(code);
		String chain = "company:" + probe;
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_chain_head WHERE chain_key = ?", Integer.class, chain))
			.isZero();

		TransactionTemplate tx = new TransactionTemplate(transactions);
		List<Long> ids = concurrently(FIRST_WRITERS, () -> tx.execute(status -> {
			long id = auditLog.record(new AuditRecord(null, "First writer test", probe, "TEST", code, "PROBED", null, null,
					null, null));
			sleep(HOLD_MILLIS);
			return id;
		}));
		assertThat(ids).hasSize(FIRST_WRITERS).doesNotHaveDuplicates();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_chain_head WHERE chain_key = ?", Integer.class, chain))
			.isEqualTo(1);
		assertThat(auditLog.verifyChain(chain).valid()).isTrue();
		assertThat(auditLog.verifyChain(chain).eventsChecked()).isEqualTo(FIRST_WRITERS);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
