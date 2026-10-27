package com.epi.procurement.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.platform.web.ApiProblemException;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Submission under concurrency and failure, at service level against SQL Server (S1-AC-05, S1-AC-08, S1DR-10,
 * S1DR-20, CR-19).
 */
class RequestSubmissionIntegrationTests extends SqlServerTestSupport {

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Autowired
	RequestService service;

	@Autowired
	UserDirectory users;

	@Autowired
	AuditLog auditLog;

	@Test
	void parallelSubmissionsGetUniqueGapFreeNumbersIncludingTheFirstOfAYear() throws Exception {
		// Move to a year in which the company has no counter row yet, so the first-of-year insert races too.
		clock.setOffset(Duration.ofDays(365L * 6));
		LocalDate today = LocalDate.ofInstant(clock.instant(), TUNIS);
		long tn = companyId("DEMO-TN-PLT");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_counter WHERE company_id = ? AND year = ?",
				Integer.class, tn, today.getYear())).isZero();
		CurrentUser requester = requester();
		int count = 24;
		List<RequestViews.Detail> drafts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			drafts.add(service.create(requester, completePayload("Parallel " + i, today.plusDays(10))));
		}

		List<RequestViews.Detail> submitted = runConcurrently(drafts.stream()
			.<Callable<RequestViews.Detail>>map(d -> () -> service.submit(requester, d.id(), d.version()))
			.toList());

		Set<String> numbers = new HashSet<>();
		Set<Integer> sequences = new HashSet<>();
		String prefix = "DEMO-TN-PLT-PR-" + today.getYear() + "-";
		for (RequestViews.Detail detail : submitted) {
			assertThat(detail.status()).isEqualTo("CREATED");
			assertThat(detail.number()).startsWith(prefix);
			numbers.add(detail.number());
			sequences.add(Integer.parseInt(detail.number().substring(prefix.length())));
		}
		assertThat(numbers).hasSize(count);
		assertThat(sequences).containsExactlyInAnyOrderElementsOf(range(1, count));
		assertThat(jdbc.queryForObject("SELECT next_value FROM document_counter WHERE company_id = ? AND year = ? "
				+ "AND kind = 'PURCHASE_REQUEST'", Long.class, tn, today.getYear())).isEqualTo(count + 1L);
		assertThat(auditLog.verifyChain("company:" + tn).valid()).isTrue();
	}

	@Test
	void concurrentSubmissionsOfTheSameRequestSucceedExactlyOnce() throws Exception {
		CurrentUser requester = requester();
		RequestViews.Detail draft = service.create(requester, completePayload("Double submit", futureDate()));
		long counterBefore = counter();

		List<Callable<Object>> attempts = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			attempts.add(() -> {
				try {
					return service.submit(requester, draft.id(), draft.version());
				}
				catch (ApiProblemException ex) {
					return ex;
				}
			});
		}
		List<Object> results = runConcurrently(attempts);

		assertThat(results.stream().filter(RequestViews.Detail.class::isInstance)).hasSize(1);
		assertThat(results.stream().filter(ApiProblemException.class::isInstance).map(ApiProblemException.class::cast))
			.hasSize(9)
			.allSatisfy(ex -> assertThat(ex.getCode()).isIn("invalid-state", "stale-version"));
		assertThat(counter()).isEqualTo(counterBefore + 1);
	}

	@Test
	void anAuditFailureRollsBackTheWholeSubmission() {
		CurrentUser requester = requester();
		RequestViews.Detail draft = service.create(requester, completePayload("Atomic", futureDate()));
		long counterBefore = counter();
		jdbc.execute("""
				CREATE TRIGGER trg_test_fail_submission ON audit_event AFTER INSERT AS
				IF EXISTS (SELECT 1 FROM inserted WHERE action = 'SUBMITTED' AND entity_id = '%d')
				    THROW 52000, 'Forced audit failure (test)', 1;""".formatted(draft.id()));
		try {
			assertThatThrownBy(() -> service.submit(requester, draft.id(), draft.version()))
				.isInstanceOf(DataAccessException.class);
		}
		finally {
			jdbc.execute("DROP TRIGGER trg_test_fail_submission");
		}

		RequestViews.Detail after = service.get(requester, draft.id());
		assertThat(after.status()).isEqualTo("DRAFT");
		assertThat(after.number()).isNull();
		assertThat(after.version()).isEqualTo(draft.version());
		assertThat(after.transitions()).extracting(RequestViews.Transition::toStatus).containsExactly("DRAFT");
		assertThat(counter()).isEqualTo(counterBefore);

		// The retry succeeds with the next number: no gap was consumed by the failed attempt.
		RequestViews.Detail submitted = service.submit(requester, draft.id(), draft.version());
		assertThat(submitted.number()).endsWith("-%06d".formatted(counterBefore));
	}

	/**
	 * CR-19: rows inserted by JPA (pooled sequence allocation) and by plain SQL (DEFAULT NEXT VALUE FOR) never
	 * collide.
	 */
	@Test
	void jpaAndSqlInsertsShareTheSequenceWithoutCollisions() {
		CurrentUser requester = requester();
		long first = sqlInsertDraft(requester);
		long second = service.create(requester, completePayload("JPA 1", futureDate())).id();
		long third = sqlInsertDraft(requester);
		long fourth = service.create(requester, completePayload("JPA 2", futureDate())).id();
		long fifth = sqlInsertDraft(requester);

		assertThat(Set.of(first, second, third, fourth, fifth)).hasSize(5);
	}

	private long sqlInsertDraft(CurrentUser user) {
		return jdbc.queryForObject("""
				INSERT INTO purchase_request (group_id, status, stage, currency_code, requesting_company_id,
				    charged_company_id, buying_company_id, requester_user_id, entered_by_user_id, updated_by_user_id,
				    current_owner_user_id, owner_since, title)
				OUTPUT INSERTED.id
				VALUES (?, 'DRAFT', 'REQUEST', 'TND', ?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), N'SQL insert')""", Long.class,
				user.groupId(), user.companyId(), user.companyId(), user.companyId(), user.id(), user.id(), user.id(),
				user.id());
	}

	private CurrentUser requester() {
		return users.loadCurrentUser(userId("requester.tn@demo.local")).orElseThrow();
	}

	private long counter() {
		List<Long> values = jdbc.queryForList("SELECT next_value FROM document_counter WHERE company_id = ? AND year = ? "
				+ "AND kind = 'PURCHASE_REQUEST'", Long.class, companyId("DEMO-TN-PLT"),
				LocalDate.ofInstant(clock.instant(), TUNIS).getYear());
		// The next number to be issued (a missing row means 1).
		return values.isEmpty() ? 1 : values.get(0);
	}

	private LocalDate futureDate() {
		return LocalDate.ofInstant(clock.instant(), TUNIS).plusDays(10);
	}

	private RequestPayload completePayload(String title, LocalDate requiredDate) {
		long tn = companyId("DEMO-TN-PLT");
		long group = jdbc.queryForObject("SELECT group_id FROM company WHERE id = ?", Long.class, tn);
		Long category = jdbc.queryForObject("SELECT id FROM request_category WHERE group_id = ? AND code = 'DEMO-SPARE'",
				Long.class, group);
		Long department = jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, tn);
		return new RequestPayload(null, title, category, "NORMAL", "LOCAL", "TND", null, department, null, null, null,
				null, null, null, null, null, null, null, null, requiredDate, null,
				List.of(new RequestPayload.LinePayload(null, "Bearing", null, BigDecimal.TEN, "PCE", new BigDecimal("12.5"), null,
						null)),
				null, null, null, null, null, null, null);
	}

	private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				try {
					results.add(future.get());
				}
				catch (ExecutionException ex) {
					throw new AssertionError("A concurrent task failed", ex.getCause());
				}
			}
			return results;
		}
		finally {
			pool.shutdown();
		}
	}

	private static List<Integer> range(int from, int to) {
		List<Integer> values = new ArrayList<>();
		for (int i = from; i <= to; i++) {
			values.add(i);
		}
		return values;
	}

}
