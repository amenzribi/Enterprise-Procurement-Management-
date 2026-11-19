package com.epi.procurement.auditcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.epi.procurement.audit.AuditChainVerification;
import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.audit.AuditRecord;
import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

/**
 * Audit Center (Sprint 2, P1.3): global administrators only, server-side filters and keyset paging, a bounded CSV
 * export that cannot inject formulas, chain verification in the background, and every use recorded in the trail.
 */
class AuditCenterIntegrationTests extends SqlServerTestSupport {

	@Autowired
	AuditLog auditLog;

	private static final String EVENTS = "/api/v1/admin/audit/events";

	private long globalId() {
		return userId("global.admin@demo.local");
	}

	private int accessEvents(String action) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_type = 'AUDIT_TRAIL' AND action = ? "
				+ "AND actor_user_id = ?", Integer.class, action, globalId());
	}

	/** Records {@code count} events about one fresh entity; returns its id. */
	private String events(long companyId, int count, String comment) {
		String entity = "ac-" + UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < count; i++) {
			auditLog.record(new AuditRecord(null, "Audit Center test", companyId, "TEST", entity, "PROBED", null, null,
					comment == null ? "event " + i : comment, null));
		}
		return entity;
	}

	@Test
	void onlyGlobalAdministratorsReachTheAuditCenter() {
		client().get(EVENTS).expectStatus(401);
		for (String who : new String[] { "admin@demo.local", "requester.tn@demo.local", "ceo@demo.local",
				"contact.skf@demo.local" }) {
			ApiClient user = signedIn(who);
			user.get(EVENTS).expectStatus(403);
			user.get(EVENTS + "/export").expectStatus(403);
			user.get("/api/v1/admin/audit/verifications/current").expectStatus(403);
			user.post("/api/v1/admin/audit/verifications", "{}").expectStatus(403);
		}
		signedIn("global.admin@demo.local").get(EVENTS + "?size=1").expectStatus(200);
	}

	@Test
	void filtersAndKeysetPagesReturnEachEventOnceNewestFirst() {
		long tn = companyId("DEMO-TN-PLT");
		String entity = events(tn, 5, null);
		ApiClient global = signedIn("global.admin@demo.local");
		int searchesBefore = accessEvents("AUDIT_SEARCHED");

		List<Number> ids = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			ApiClient.Response page = global.get(EVENTS + "?entityType=TEST&entityId=" + entity + "&companyId=" + tn
					+ "&action=PROBED&size=2" + (cursor == null ? "" : "&cursor=" + cursor)).expectStatus(200);
			List<Number> pageIds = page.json("$.items[*].id");
			assertThat(pageIds).hasSizeLessThanOrEqualTo(2);
			ids.addAll(pageIds);
			List<String> companies = page.json("$.items[*].companyName");
			assertThat(companies).allSatisfy(name -> assertThat(name).isNotBlank());
			cursor = page.json("$.nextCursor");
			pages++;
		}
		while (cursor != null && pages < 10);

		assertThat(ids).hasSize(5).doesNotHaveDuplicates();
		assertThat(ids.stream().map(Number::longValue).toList())
			.isSortedAccordingTo((a, b) -> Long.compare(b, a));
		// The search was recorded once, not once per page.
		assertThat(accessEvents("AUDIT_SEARCHED")).isEqualTo(searchesBefore + 1);
		// Another company's filter does not see these events.
		assertThat((List<?>) global.get(EVENTS + "?entityId=" + entity + "&companyId=" + companyId("DEMO-FR-PLT"))
			.expectStatus(200)
			.json("$.items")).isEmpty();
	}

	@Test
	void invalidFiltersAreRefusedBeforeAnyQuery() {
		ApiClient global = signedIn("global.admin@demo.local");
		global.get(EVENTS + "?action=drop%20table").expectStatus(422);
		global.get(EVENTS + "?from=yesterday").expectStatus(422);
		global.get(EVENTS + "?from=2026-11-18T00:00:00Z&to=2026-11-17T00:00:00Z").expectStatus(422);
		global.get(EVENTS + "?cursor=not-a-cursor").expectStatus(422);
		global.get(EVENTS + "/999999999").expectStatus(404);
	}

	@Test
	void theExportIsCsvWithoutFormulasAndIsRecorded() {
		long tn = companyId("DEMO-TN-PLT");
		String entity = events(tn, 3, "=HYPERLINK(\"http://evil.example\",\"click\")");
		ApiClient global = signedIn("global.admin@demo.local");
		int exportsBefore = accessEvents("AUDIT_EXPORTED");

		ApiClient.Response csv = global.get(EVENTS + "/export?entityType=TEST&entityId=" + entity).expectStatus(200);
		String body = csv.body();
		assertThat(body).startsWith("﻿\"id\",\"occurred_at_utc\"");
		assertThat(body.split("\r\n")).hasSize(4);
		assertThat(body).contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"click\"\")\"")
			.doesNotContain(",\"=HYPERLINK");

		assertThat(accessEvents("AUDIT_EXPORTED")).isEqualTo(exportsBefore + 1);
		String payload = jdbc.queryForObject("""
				SELECT TOP 1 payload_json FROM audit_event WHERE entity_type = 'AUDIT_TRAIL' AND action = 'AUDIT_EXPORTED'
				AND actor_user_id = ? ORDER BY id DESC""", String.class, globalId());
		assertThat(payload).contains("\"entityId\":\"" + entity + "\"").contains("\"rows\":3").contains("\"truncated\":false");
	}

	@Test
	void verificationRunsInTheBackgroundAndDetectsTampering() throws Exception {
		ApiClient global = signedIn("global.admin@demo.local");
		Map<String, Object> result = verify(global);
		assertThat(result.get("outcome")).isEqualTo("VALID");
		assertThat(((Number) result.get("eventsChecked")).longValue()).isPositive();
		assertThat(accessEvents("AUDIT_VERIFICATION_COMPLETED")).isPositive();

		// Tamper with one event as the schema owner (trigger disabled), then restore it.
		long companyId = companyId("DEMO-FR-PLT");
		String entity = events(companyId, 1, "original");
		long target = jdbc.queryForObject("SELECT id FROM audit_event WHERE entity_id = ?", Long.class, entity);
		JdbcTemplate owner = ownerJdbc();
		owner.execute("DISABLE TRIGGER trg_audit_event_append_only ON audit_event");
		try {
			owner.update("UPDATE audit_event SET comment = 'altered' WHERE id = ?", target);
		}
		finally {
			owner.execute("ENABLE TRIGGER trg_audit_event_append_only ON audit_event");
		}
		try {
			Map<String, Object> tampered = verify(global);
			assertThat(tampered.get("outcome")).isEqualTo("INVALID");
			assertThat(tampered.get("invalidChain")).isEqualTo("company:" + companyId);
			assertThat(((Number) tampered.get("firstInvalidEventId")).longValue()).isEqualTo(target);
		}
		finally {
			owner.execute("DISABLE TRIGGER trg_audit_event_append_only ON audit_event");
			try {
				owner.update("UPDATE audit_event SET comment = 'original' WHERE id = ?", target);
			}
			finally {
				owner.execute("ENABLE TRIGGER trg_audit_event_append_only ON audit_event");
			}
		}
		assertThat(verify(global).get("outcome")).isEqualTo("VALID");
	}

	@Test
	void batchedVerificationGivesTheSameAnswerAsTheSingleReading() {
		long companyId = companyId("DEMO-CPA");
		events(companyId, 7, null);
		String chain = "company:" + companyId;
		AuditChainVerification whole = auditLog.verifyChain(chain);
		List<Long> progress = new ArrayList<>();
		AuditChainVerification batched = auditLog.verifyChainInBatches(chain, 3, progress::add);
		assertThat(batched).isEqualTo(whole);
		assertThat(whole.valid()).isTrue();
		// Several batches, each reporting the running total.
		assertThat(progress).hasSizeGreaterThan(1).isSorted();
		assertThat(progress.get(progress.size() - 1)).isEqualTo(whole.eventsChecked());
	}

	/** Starts a verification and waits for its result (at most 60 s). */
	private Map<String, Object> verify(ApiClient global) throws InterruptedException {
		global.post("/api/v1/admin/audit/verifications", "{}").expectStatus(202);
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			Map<String, Object> state = global.get("/api/v1/admin/audit/verifications/current").expectStatus(200).json("$");
			if (!"RUNNING".equals(state.get("outcome"))) {
				return state;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("verification did not finish within 60 s");
	}

}
