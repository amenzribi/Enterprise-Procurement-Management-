package com.epi.procurement.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.audit.AuditLog;
import com.epi.procurement.notification.OutboxSender;
import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sprint 2 approval circuits over real HTTP against SQL Server (S2-AC-02..13): the v3.0 journey with a one-tap
 * e-mail approval, questions, return and resubmission, rejection, races, idempotency, token security, rules and
 * exchange rates, reminders, absences, self-exclusion and company isolation of the new endpoints.
 */
class ApprovalIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	private static final Pattern LINK = Pattern.compile("/a#([A-Za-z0-9_-]{43})");

	@Autowired
	AuditLog auditLog;

	@Autowired
	OutboxSender outbox;

	@Autowired
	ApprovalReminders reminders;

	@Test
	void usdRequestGoesThroughManagerByEmailLinkThenBudgetApprover() throws Exception {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = submit(sami, request("Pump P-204 seals", "USD", 8400, "DEMO-SPARE"));

		// 8 400 USD x 3.1 = 26 040 TND: manager then budget approver (Demo rules).
		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + id + "/approval").expectStatus(200);
		assertThat((String) approval.json("$.userStatus")).isEqualTo("SUBMITTED");
		assertThat(((Number) approval.json("$.rounds[0].amounts.companyAmount")).doubleValue()).isEqualTo(26040.0);
		assertThat((String) approval.json("$.rounds[0].amounts.companyCurrency")).isEqualTo("TND");
		assertThat((List<String>) approval.json("$.actions")).contains("APPROVE", "REJECT", "RETURN", "QUESTION");
		assertThat((List<String>) approval.json("$.nextActors[*].displayName")).anyMatch(n -> n.startsWith("Karim"))
			.anyMatch(n -> n.startsWith("Hichem"));

		// The e-mail is written as an .eml file (file mail mode) and carries the one-tap link.
		String token = linkToken(userId("manager.tn@demo.local"), id);
		outbox.sendDue();
		try (Stream<Path> files = Files.list(Path.of("target/test-mail"))) {
			assertThat(files.anyMatch(f -> htmlOf(f).contains("/a#" + token))).isTrue();
		}

		// Opening the link (summary) never approves.
		ApiClient anonymous = client();
		anonymous.csrf();
		ApiClient.Response summary = anonymous.post("/api/v1/approval-links/summary", tokenBody(token)).expectStatus(200);
		assertThat((String) summary.json("$.title")).isEqualTo("Pump P-204 seals");
		assertThat((String) summary.json("$.lines[0].unit")).isEqualTo("PCE");
		assertThat(((Number) summary.json("$.lineCount")).intValue()).isEqualTo(1);
		assertThat(summary.header("Cache-Control")).contains("no-store");
		assertThat(summary.header("Referrer-Policy")).isEqualTo("no-referrer");
		// ANY stage with two Sfax maintenance managers (Karim, Hichem).
		assertThat(pendingTasks(id)).hasSize(2);
		// QA2-D-08: one token per e-mail (the HTML and text parts share it).
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_link_token k JOIN approval_task t ON t.id = k.task_id "
				+ "WHERE t.request_id = ? AND t.assignee_user_id = ?", Integer.class, id, userId("manager.tn@demo.local")))
			.isEqualTo(1);
		// S2CR-01: no usable token is stored in the outbox, before or after sending.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE body_html LIKE ? OR body_text LIKE ?",
				Integer.class, "%" + token + "%", "%" + token + "%")).isZero();

		ApiClient.Response approved = anonymous.post("/api/v1/approval-links/approve", tokenBody(token)).expectStatus(200);
		assertThat((String) approved.json("$.outcome")).isEqualTo("APPROVED");
		// Single use.
		assertThat(anonymous.post("/api/v1/approval-links/approve", tokenBody(token)).expectStatus(404).code())
			.isEqualTo("link-invalid");

		// Stage 2: budget approver, through the inbox.
		ApiClient leila = signedIn("budget.tn@demo.local");
		ApiClient.Response inbox = leila.get("/api/v1/approvals/inbox?size=100").expectStatus(200);
		int index = ((List<Integer>) inbox.json("$.items[*].requestId")).indexOf((int) id);
		assertThat(index).isGreaterThanOrEqualTo(0);
		long task = ((Number) inbox.json("$.items[" + index + "].taskId")).longValue();
		int version = ((Number) inbox.json("$.items[" + index + "].taskVersion")).intValue();
		assertThat(((Number) leila.get("/api/v1/approvals/inbox/count").json("$.pending")).intValue()).isPositive();
		ApiClient.Response done = leila.post("/api/v1/approvals/tasks/" + task + "/approve",
				"{\"version\":" + version + ",\"comment\":\"Budget OK\"}").expectStatus(200);
		assertThat((String) done.json("$.userStatus")).isEqualTo("APPROVED");

		ApiClient.Response detail = sami.get("/api/v1/requests/" + id).expectStatus(200);
		assertThat((String) detail.json("$.status")).isEqualTo("IN_PURCHASING_REVIEW");
		assertThat((String) detail.json("$.userStatus")).isEqualTo("APPROVED");
		assertThat((List<String>) sami.get("/api/v1/requests/" + id + "/approval").json("$.events[*].kind"))
			.contains("TASK_CREATED", "APPROVED", "ROUND_APPROVED");
		assertThat((String) jdbc.queryForObject("SELECT TOP (1) channel FROM approval_task_event WHERE request_id = ? "
				+ "AND kind = 'APPROVED' ORDER BY id", String.class, id)).isEqualTo("EMAIL");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE request_id = ? AND kind = "
				+ "'REQUEST_APPROVED' AND user_id = ?", Integer.class, id, userId("requester.tn@demo.local"))).isEqualTo(1);
		assertThat(auditLog.verifyChain("company:" + companyId("DEMO-TN-PLT")).valid()).isTrue();
	}

	@Test
	void questionReturnResubmitAndRejection() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient karim = signedIn("manager.tn@demo.local");
		long id = submit(sami, request("Gloves", "TND", 100, "DEMO-SPARE"));

		long[] task = myTask(karim, id);
		karim.post("/api/v1/approvals/tasks/" + task[0] + "/question", "{\"version\":" + task[1] + "}")
			.expectStatus(422);
		ApiClient.Response asked = karim.post("/api/v1/approvals/tasks/" + task[0] + "/question",
				"{\"version\":" + task[1] + ",\"question\":\"Which size?\"}").expectStatus(200);
		assertThat((Boolean) asked.json("$.myTask.questionOpen")).isTrue();
		assertThat((List<String>) asked.json("$.actions")).doesNotContain("QUESTION");

		ApiClient.Response forSami = sami.get("/api/v1/requests/" + id + "/approval").expectStatus(200);
		assertThat((List<String>) forSami.json("$.actions")).contains("ANSWER");
		assertThat((String) forSami.json("$.openQuestions[0].question")).isEqualTo("Which size?");
		sami.post("/api/v1/requests/" + id + "/answers", "{\"taskId\":" + task[0] + ",\"answer\":\"Size L\"}")
			.expectStatus(200);
		assertThat(karim.post("/api/v1/requests/" + id + "/answers",
				"{\"taskId\":" + task[0] + ",\"answer\":\"x\"}").expectStatus(403).code()).isEqualTo("forbidden");

		task = myTask(karim, id);
		karim.post("/api/v1/approvals/tasks/" + task[0] + "/return", "{\"version\":" + task[1] + "}").expectStatus(422);
		karim.post("/api/v1/approvals/tasks/" + task[0] + "/return",
				"{\"version\":" + task[1] + ",\"reason\":\"Add the datasheet\"}").expectStatus(200);
		ApiClient.Response returned = sami.get("/api/v1/requests/" + id).expectStatus(200);
		assertThat((String) returned.json("$.userStatus")).isEqualTo("RETURNED");
		assertThat((List<String>) returned.json("$.actions")).containsExactly("EDIT", "SUBMIT");
		String number = returned.json("$.number");

		// The author edits and resubmits: same number, round 2.
		Map<String, Object> body = request("Gloves size L", "TND", 120, "DEMO-SPARE");
		body.put("version", returned.version());
		ApiClient.Response edited = sami.put("/api/v1/requests/" + id, json(body)).expectStatus(200);
		ApiClient.Response resubmitted = sami.post("/api/v1/requests/" + id + "/submit",
				"{\"version\":" + edited.version() + "}").expectStatus(200);
		assertThat((String) resubmitted.json("$.number")).isEqualTo(number);
		assertThat(((Number) resubmitted.json("$.roundNo")).intValue()).isEqualTo(2);

		task = myTask(karim, id);
		karim.post("/api/v1/approvals/tasks/" + task[0] + "/reject",
				"{\"version\":" + task[1] + ",\"reason\":\"Not needed\"}").expectStatus(200);
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.userStatus")).isEqualTo("REJECTED");
		// Rejection is terminal: no edit, no further decision.
		sami.post("/api/v1/requests/" + id + "/submit", "{\"version\":" + resubmitted.version() + "}").expectStatus(409);
		assertThat(karim.post("/api/v1/approvals/tasks/" + task[0] + "/approve", "{\"version\":" + (task[1] + 1) + "}")
			.expectStatus(409)
			.code()).isEqualTo("task-not-pending");
	}

	@Test
	void linkApprovalAndInboxRejectionRaceHasExactlyOneWinner() throws Exception {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient karim = signedIn("manager.tn@demo.local");
		long id = submit(sami, request("Race", "TND", 50, "DEMO-SPARE"));
		String token = linkToken(userId("manager.tn@demo.local"), id);
		long[] task = myTask(karim, id);
		ApiClient anonymous = client();
		anonymous.csrf();

		List<ApiClient.Response> results = concurrently(List.of(
				() -> anonymous.post("/api/v1/approval-links/approve", tokenBody(token)),
				() -> karim.post("/api/v1/approvals/tasks/" + task[0] + "/reject",
						"{\"version\":" + task[1] + ",\"reason\":\"No\"}")));
		assertThat(results.stream().filter(r -> r.status() == 200)).hasSize(1);
		String status = sami.get("/api/v1/requests/" + id).json("$.status");
		assertThat(status).isIn("APPROVED", "REJECTED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE request_id = ? AND status IN "
				+ "('APPROVED', 'REJECTED')", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void idempotencyKeyReplaysTheFirstResult() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient karim = signedIn("manager.tn@demo.local");
		long id = submit(sami, request("Idempotent", "TND", 40, "DEMO-SPARE"));
		long[] task = myTask(karim, id);
		String body = "{\"version\":" + task[1] + "}";
		Map<String, String> key = Map.of("Idempotency-Key", "tap-" + id);
		ApiClient.Response first = karim.post("/api/v1/approvals/tasks/" + task[0] + "/approve", body, key)
			.expectStatus(200);
		ApiClient.Response replay = karim.post("/api/v1/approvals/tasks/" + task[0] + "/approve", body, key)
			.expectStatus(200);
		assertThat(replay.body()).isEqualTo(first.body());
		assertThat(karim.post("/api/v1/approvals/tasks/" + task[0] + "/reject", "{\"version\":1,\"reason\":\"x\"}", key)
			.expectStatus(422)
			.code()).isEqualTo("idempotency-key-reused");
	}

	@Test
	void invalidLinksAllLookTheSame() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = submit(sami, request("Links", "TND", 30, "DEMO-SPARE"));
		String token = linkToken(userId("manager.tn@demo.local"), id);

		ApiClient anonymous = client();
		anonymous.csrf();
		ApiClient.Response unknown = anonymous.post("/api/v1/approval-links/summary",
				tokenBody("x".repeat(43))).expectStatus(404);
		// Another signed-in user presenting the link.
		ApiClient leila = signedIn("budget.tn@demo.local");
		ApiClient.Response wrongUser = leila.post("/api/v1/approval-links/approve", tokenBody(token)).expectStatus(404);
		assertThat(pendingTasks(id)).hasSize(2);
		// Expired.
		clock.advance(Duration.ofHours(73));
		ApiClient.Response expired = anonymous.post("/api/v1/approval-links/summary", tokenBody(token)).expectStatus(404);
		clock.reset();
		// Decided in the app: the link dies with the task.
		ApiClient karim = signedIn("manager.tn@demo.local");
		long[] task = myTask(karim, id);
		karim.post("/api/v1/approvals/tasks/" + task[0] + "/approve", "{\"version\":" + task[1] + "}").expectStatus(200);
		ApiClient.Response decided = anonymous.post("/api/v1/approval-links/approve", tokenBody(token)).expectStatus(404);
		for (ApiClient.Response response : List.of(wrongUser, expired, decided)) {
			assertThat(withoutInstance(response.body())).isEqualTo(withoutInstance(unknown.body()));
		}
		// Only the hash is stored.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_link_token WHERE token_hash = ?", Integer.class,
				token)).isZero();
	}

	@Test
	void ruleSpecificityOverlapExchangeRatesAndSelfExclusion() {
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long it = categoryId("DEMO-IT");
		long sami = userId("requester.tn@demo.local");
		// A category rule naming the requester as the only approver: more specific than the company rules.
		ApiClient.Response rule = admin.post("/api/v1/admin/approval-rules", json(Map.of("companyId", tn, "name",
				"IT self (test)", "categoryId", it, "minAmount", 0, "stages", List.of(Map.of("name", "Named", "approverType",
						"NAMED_USER", "approverUserId", sami, "policy", "ANY", "slaHours", 8))))).expectStatus(201);
		long ruleId = rule.id();
		admin.post("/api/v1/admin/approval-rules/" + ruleId + "/activate", null).expectStatus(200);
		try {
			// Overlapping rule of the same scope is refused at activation.
			long overlapping = admin.post("/api/v1/admin/approval-rules", json(Map.of("companyId", tn, "name",
					"IT overlap (test)", "categoryId", it, "minAmount", 100, "stages", List.of(Map.of("name", "M",
							"approverType", "DEPARTMENT_MANAGER", "policy", "ANY", "slaHours", 8))))).expectStatus(201).id();
			assertThat(admin.post("/api/v1/admin/approval-rules/" + overlapping + "/activate", null).expectStatus(422)
				.code()).isEqualTo("ambiguous-approval-rule");

			// No self-approval: the only approver is the requester and there is no escalation role.
			ApiClient samiClient = signedIn("requester.tn@demo.local");
			ApiClient.Response draft = samiClient.post("/api/v1/requests", json(request("Laptop", "TND", 900, "DEMO-IT")))
				.expectStatus(201);
			assertThat((String) samiClient.get("/api/v1/requests/" + draft.id() + "/approval-preview").json("$.problem"))
				.isEqualTo("no-eligible-approver");
			assertThat(samiClient.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
				.expectStatus(422)
				.code()).isEqualTo("no-eligible-approver");
			assertThat((String) samiClient.get("/api/v1/requests/" + draft.id()).json("$.status")).isEqualTo("DRAFT");
			// Administrators are told, even though the submission rolled back.
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE kind = 'NO_ELIGIBLE_APPROVER' "
					+ "AND recipient_user_id = ?", Integer.class, userId("admin@demo.local"))).isPositive();
		}
		finally {
			admin.post("/api/v1/admin/approval-rules/" + ruleId + "/retire", null).expectStatus(200);
		}

		// Missing rate: JPY has no rate to TND.
		ApiClient samiClient = signedIn("requester.tn@demo.local");
		ApiClient.Response jpy = samiClient.post("/api/v1/requests", json(request("Yen", "JPY", 1000, "DEMO-SPARE")))
			.expectStatus(201);
		assertThat(samiClient.post("/api/v1/requests/" + jpy.id() + "/submit", "{\"version\":" + jpy.version() + "}")
			.expectStatus(422)
			.code()).isEqualTo("missing-exchange-rate");

		// Inverse pair: a TND request in the French company converts with 1 / (EUR->TND 3.4).
		ApiClient claire = signedIn("requester.fr@demo.local");
		Map<String, Object> fr = frRequest("Inverse rate", 340);
		long frId = submit(claire, fr);
		ApiClient.Response frApproval = claire.get("/api/v1/requests/" + frId + "/approval").expectStatus(200);
		assertThat(((Number) frApproval.json("$.rounds[0].amounts.companyAmount")).doubleValue()).isEqualTo(100.0);
		assertThat((String) frApproval.json("$.rounds[0].amounts.companyCurrency")).isEqualTo("EUR");
	}

	@Test
	void remindersAndEscalationsAreSentOnce() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = submit(sami, request("Reminder", "TND", 20, "DEMO-SPARE"));
		long task = pendingTasks(id).get(0);
		int tasks = pendingTasks(id).size();
		clock.advance(Duration.ofDays(10));
		reminders.run();
		reminders.run();
		assertThat(jdbc.queryForList("SELECT kind FROM approval_task_reminder WHERE task_id = ? ORDER BY kind",
				String.class, task)).containsExactly("ESCALATION", "REMINDER");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE kind = 'TASK_REMINDER' AND "
				+ "body_html LIKE ?", Integer.class, "%/requests/" + id + "\"%")).as("one reminder per task").isEqualTo(tasks);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE kind = 'TASK_ESCALATION' AND "
				+ "body_html LIKE ?", Integer.class, "%/requests/" + id + "\"%")).as("one escalation per task").isEqualTo(tasks);
	}

	/**
	 * Absence routing applies only at task creation. Pre-existing tasks stay with the original assignee; ending an
	 * absence does not pull already-delegated tasks back.
	 */
	@Test
	void absenceAffectsOnlyTasksCreatedDuringThePeriod() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient karim = signedIn("manager.tn@demo.local");
		long karimId = userId("manager.tn@demo.local");
		long hichemId = userId("manager2.tn@demo.local");

		// Task created before absence: stays with Karim (and Hichem as ANY peer).
		long beforeId = submit(sami, request("Before absence IT", "TND", 15, "DEMO-SPARE"));
		assertThat(jdbc.queryForList(
				"SELECT assignee_user_id FROM approval_task WHERE request_id = ? AND status = 'PENDING'", Long.class,
				beforeId)).contains(karimId);

		Instant now = clock.instant();
		long absence = karim.post("/api/v1/me/absences",
				"{\"delegateUserId\":" + hichemId + ",\"startsAt\":\"" + now.minusSeconds(30) + "\",\"endsAt\":\""
						+ now.plus(Duration.ofDays(1)) + "\",\"reason\":\"IT controlled absence\"}")
			.expectStatus(201)
			.id();
		try {
			long duringId = submit(sami, request("During absence IT", "TND", 15, "DEMO-SPARE"));
			List<Map<String, Object>> during = jdbc.queryForList(
					"SELECT assignee_user_id, original_user_id, routing_note FROM approval_task WHERE request_id = ?",
					duringId);
			assertThat(during).anySatisfy(t -> {
				assertThat(((Number) t.get("assignee_user_id")).longValue()).isEqualTo(hichemId);
				assertThat(((Number) t.get("original_user_id")).longValue()).isEqualTo(karimId);
				assertThat(t.get("routing_note")).isEqualTo("ABSENCE_DELEGATE");
			});
			// Pre-absence task unchanged.
			assertThat(jdbc.queryForList(
					"SELECT assignee_user_id FROM approval_task WHERE request_id = ? AND status = 'PENDING'", Long.class,
					beforeId)).contains(karimId);

			karim.delete("/api/v1/me/absences/" + absence).expectStatus(204);
			absence = 0;
			// Already delegated task remains with Hichem after absence ends.
			assertThat(jdbc.queryForList(
					"SELECT assignee_user_id FROM approval_task WHERE request_id = ? AND routing_note = 'ABSENCE_DELEGATE'",
					Long.class, duringId)).containsOnly(hichemId);

			long afterId = submit(sami, request("After absence IT", "TND", 15, "DEMO-SPARE"));
			assertThat(jdbc.queryForList(
					"SELECT assignee_user_id FROM approval_task WHERE request_id = ? AND status = 'PENDING'", Long.class,
					afterId)).contains(karimId);
		}
		finally {
			if (absence > 0) {
				karim.delete("/api/v1/me/absences/" + absence).expectStatus(204);
			}
		}
	}

	@Test
	void globalAdminCanAdministerAbsencesLikeCompanyAdmin() {
		ApiClient global = signedIn("global.admin@demo.local");
		Instant now = clock.instant();
		long absence = global.post("/api/v1/admin/absences",
				"{\"userId\":" + userId("manager.tn@demo.local") + ",\"delegateUserId\":"
						+ userId("manager2.tn@demo.local") + ",\"startsAt\":\"" + now.plus(Duration.ofDays(10))
						+ "\",\"endsAt\":\"" + now.plus(Duration.ofDays(12))
						+ "\",\"reason\":\"GLOBAL_ADMIN IT absence\"}")
			.expectStatus(201)
			.id();
		global.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
	}

	@Test
	void absenceDelegationRoutesTasksToTheDelegate() {
		ApiClient leila = signedIn("budget.tn@demo.local");
		long ceo = userId("ceo@demo.local");
		Instant now = clock.instant();
		String period = ",\"startsAt\":\"" + now.minusSeconds(60) + "\",\"endsAt\":\"" + now.plus(Duration.ofDays(2)) + "\"}";
		// The CEO does not hold BUDGET_APPROVER: not eligible for a self-service absence.
		assertThat(leila.post("/api/v1/me/absences", "{\"delegateUserId\":" + ceo + period).expectStatus(422).code())
			.isEqualTo("validation-failed");
		assertThat(leila.post("/api/v1/me/absences", "{\"delegateUserId\":" + userId("budget.tn@demo.local") + period)
			.expectStatus(422)
			.code()).isEqualTo("validation-failed");

		ApiClient admin = signedIn("admin@demo.local");
		long absence = admin.post("/api/v1/admin/absences", "{\"userId\":" + userId("budget.tn@demo.local")
				+ ",\"adminOverride\":true,\"delegateUserId\":" + ceo + period).expectStatus(201).id();
		try {
			assertThat(admin.post("/api/v1/admin/absences", "{\"userId\":" + userId("budget.tn@demo.local")
					+ ",\"adminOverride\":true,\"delegateUserId\":" + ceo + period).expectStatus(422).code())
				.isEqualTo("absence-overlap");
			ApiClient sami = signedIn("requester.tn@demo.local");
			ApiClient karim = signedIn("manager.tn@demo.local");
			long id = submit(sami, request("Delegated budget", "TND", 6000, "DEMO-SPARE"));
			long[] task = myTask(karim, id);
			karim.post("/api/v1/approvals/tasks/" + task[0] + "/approve", "{\"version\":" + task[1] + "}").expectStatus(200);
			ApiClient.Response approval = sami.get("/api/v1/requests/" + id + "/approval").expectStatus(200);
			assertThat(((Number) approval.json("$.nextActors[0].id")).longValue()).isEqualTo(ceo);
			assertThat((String) approval.json("$.rounds[0].stages[1].tasks[0].onBehalfOf.displayName")).startsWith("Leila");
		}
		finally {
			admin.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
		}
	}

	@Test
	void allStageNeedsEveryApproverAndOneRejectionEndsIt() {
		ApiClient admin = signedIn("admin@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		long rule = admin.post("/api/v1/admin/approval-rules", json(Map.of("companyId", tn, "name", "Electrical ALL (test)",
				"categoryId", categoryId("DEMO-ELEC"), "minAmount", 0, "stages", List.of(Map.of("name", "Both managers",
						"approverType", "DEPARTMENT_MANAGER", "policy", "ALL", "slaHours", 8))))).expectStatus(201).id();
		admin.post("/api/v1/admin/approval-rules/" + rule + "/activate", null).expectStatus(200);
		try {
			ApiClient sami = signedIn("requester.tn@demo.local");
			ApiClient karim = signedIn("manager.tn@demo.local");
			ApiClient hichem = signedIn("manager2.tn@demo.local");

			long approved = submit(sami, request("Cable ALL", "TND", 300, "DEMO-ELEC"));
			long[] karimTask = myTask(karim, approved);
			karim.post("/api/v1/approvals/tasks/" + karimTask[0] + "/approve", "{\"version\":" + karimTask[1] + "}")
				.expectStatus(200);
			assertThat((String) sami.get("/api/v1/requests/" + approved).json("$.status")).isEqualTo("IN_APPROVAL");
			long[] hichemTask = myTask(hichem, approved);
			hichem.post("/api/v1/approvals/tasks/" + hichemTask[0] + "/approve", "{\"version\":" + hichemTask[1] + "}")
				.expectStatus(200);
			assertThat((String) sami.get("/api/v1/requests/" + approved).json("$.status")).isEqualTo("IN_PURCHASING_REVIEW");

			long rejected = submit(sami, request("Breaker ALL", "TND", 300, "DEMO-ELEC"));
			long[] task = myTask(karim, rejected);
			karim.post("/api/v1/approvals/tasks/" + task[0] + "/reject", "{\"version\":" + task[1] + ",\"reason\":\"No\"}")
				.expectStatus(200);
			assertThat((String) sami.get("/api/v1/requests/" + rejected).json("$.status")).isEqualTo("REJECTED");
			assertThat(jdbc.queryForList("SELECT status FROM approval_task WHERE request_id = ? ORDER BY id", String.class,
					rejected)).containsExactlyInAnyOrder("REJECTED", "CANCELLED");
		}
		finally {
			admin.post("/api/v1/admin/approval-rules/" + rule + "/retire", null).expectStatus(200);
		}
	}

	/**
	 * S2CR-15 / QA2-D-07, ANY stage: an absent approver whose delegate is also a direct approver gives one task,
	 * marked as absence delegation, with an ABSENCE_ROUTED event and audit record - whichever manager is absent.
	 */
	@Test
	void anyStageCollapsesAnAbsenceOntoADirectApproverInBothDirections() {
		for (String[] pair : List.of(new String[] { "manager.tn@demo.local", "manager2.tn@demo.local" },
				new String[] { "manager2.tn@demo.local", "manager.tn@demo.local" })) {
			long absent = userId(pair[0]);
			long delegate = userId(pair[1]);
			long absence = absence(pair[0], delegate);
			try {
				long id = submit(signedIn("requester.tn@demo.local"), request("Absence ANY", "TND", 15, "DEMO-SPARE"));
				List<Map<String, Object>> tasks = jdbc.queryForList("SELECT assignee_user_id, original_user_id, routing_note "
						+ "FROM approval_task WHERE request_id = ?", id);
				assertThat(tasks).hasSize(1);
				assertThat(((Number) tasks.get(0).get("assignee_user_id")).longValue()).isEqualTo(delegate);
				assertThat(((Number) tasks.get(0).get("original_user_id")).longValue()).isEqualTo(absent);
				assertThat(tasks.get(0).get("routing_note")).isEqualTo("ABSENCE_DELEGATE");
				assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task_event WHERE request_id = ? AND kind = "
						+ "'ABSENCE_ROUTED' AND on_behalf_of_user_id = ? AND target_user_id = ?", Integer.class, id, absent,
						delegate)).isEqualTo(1);
				assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_id = ? AND action = "
						+ "'APPROVAL_ABSENCE_ROUTED'", Integer.class, Long.toString(id))).isEqualTo(1);
			}
			finally {
				signedIn(pair[0]).delete("/api/v1/me/absences/" + absence).expectStatus(204);
			}
		}
		assertThat(auditLog.verifyChain("company:" + companyId("DEMO-TN-PLT")).valid()).isTrue();
	}

	/**
	 * S2CR-15, ALL stage: the delegate already holds a required approval, so the absent approver keeps their task,
	 * flagged ABSENCE_UNRESOLVED - one person never approves twice.
	 */
	@Test
	void allStageNeverGivesTwoRequiredApprovalsToOnePerson() {
		ApiClient admin = signedIn("admin@demo.local");
		long rule = admin.post("/api/v1/admin/approval-rules", json(Map.of("companyId", companyId("DEMO-TN-PLT"), "name",
				"IT ALL absence (test)", "categoryId", categoryId("DEMO-IT"), "minAmount", 0, "stages", List.of(Map.of("name",
						"Both managers", "approverType", "DEPARTMENT_MANAGER", "policy", "ALL", "slaHours", 8)))))
			.expectStatus(201)
			.id();
		admin.post("/api/v1/admin/approval-rules/" + rule + "/activate", null).expectStatus(200);
		long karim = userId("manager.tn@demo.local");
		long hichem = userId("manager2.tn@demo.local");
		long absence = absence("manager.tn@demo.local", hichem);
		try {
			long id = submit(signedIn("requester.tn@demo.local"), request("Absence ALL", "TND", 15, "DEMO-IT"));
			List<Map<String, Object>> tasks = jdbc.queryForList("SELECT assignee_user_id, original_user_id, routing_note "
					+ "FROM approval_task WHERE request_id = ? ORDER BY assignee_user_id", id);
			assertThat(tasks).hasSize(2);
			assertThat(tasks).anySatisfy(t -> {
				assertThat(((Number) t.get("assignee_user_id")).longValue()).isEqualTo(hichem);
				assertThat(t.get("original_user_id")).isNull();
			});
			assertThat(tasks).anySatisfy(t -> {
				assertThat(((Number) t.get("assignee_user_id")).longValue()).isEqualTo(karim);
				assertThat(t.get("routing_note")).isEqualTo("ABSENCE_UNRESOLVED");
			});
		}
		finally {
			signedIn("manager.tn@demo.local").delete("/api/v1/me/absences/" + absence).expectStatus(204);
			admin.post("/api/v1/admin/approval-rules/" + rule + "/retire", null).expectStatus(200);
		}
	}

	/**
	 * S2CR-15 suggestion / S2-AC-06: a request never routes to its own author. When the absent approver's delegate is
	 * the requester, the absent approver keeps the task flagged ABSENCE_UNRESOLVED and the other direct approver gets
	 * a normal task; the author holds no task.
	 */
	@Test
	void absenceDelegationNeverRoutesATaskToTheRequestAuthor() {
		ApiClient admin = signedIn("admin@demo.local");
		long karim = userId("manager.tn@demo.local");
		long hichem = userId("manager2.tn@demo.local");
		long sami = userId("requester.tn@demo.local");
		Instant now = clock.instant();
		// Only an administrator can name a delegate who does not hold the role (adminOverride).
		long absence = admin.post("/api/v1/admin/absences", "{\"userId\":" + karim + ",\"adminOverride\":true,"
				+ "\"delegateUserId\":" + sami + ",\"startsAt\":\"" + now.minusSeconds(60) + "\",\"endsAt\":\""
				+ now.plus(Duration.ofDays(1)) + "\"}").expectStatus(201).id();
		try {
			long id = submit(signedIn("requester.tn@demo.local"), request("Absence to author", "TND", 15, "DEMO-SPARE"));
			List<Map<String, Object>> tasks = jdbc.queryForList("SELECT assignee_user_id, original_user_id, routing_note, "
					+ "status FROM approval_task WHERE request_id = ? ORDER BY assignee_user_id", id);
			assertThat(tasks).extracting(t -> ((Number) t.get("assignee_user_id")).longValue())
				.containsExactlyInAnyOrder(karim, hichem)
				.doesNotContain(sami);
			assertThat(tasks).anySatisfy(t -> {
				assertThat(((Number) t.get("assignee_user_id")).longValue()).isEqualTo(karim);
				assertThat(t.get("original_user_id")).isNull();
				assertThat(t.get("routing_note")).isEqualTo("ABSENCE_UNRESOLVED");
				assertThat(t.get("status")).isEqualTo("PENDING");
			});
			assertThat(tasks).anySatisfy(t -> {
				assertThat(((Number) t.get("assignee_user_id")).longValue()).isEqualTo(hichem);
				assertThat(t.get("routing_note")).isNull();
			});
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_task_event WHERE request_id = ? AND kind = "
					+ "'ABSENCE_ROUTED'", Integer.class, id)).as("no absence routing happened").isZero();
			// The author sees the unresolved note in the tracker and is never asked to approve their own request.
			ApiClient author = signedIn("requester.tn@demo.local");
			assertThat((List<String>) author.get("/api/v1/requests/" + id + "/approval").expectStatus(200)
				.json("$.rounds[0].stages[0].tasks[*].routingNote")).contains("ABSENCE_UNRESOLVED");
			assertThat((List<Integer>) author.get("/api/v1/approvals/inbox?size=100").expectStatus(200)
				.json("$.items[*].requestId")).doesNotContain((int) id);
		}
		finally {
			admin.delete("/api/v1/admin/absences/" + absence).expectStatus(204);
		}
	}

	private long absence(String absentEmail, long delegateId) {
		Instant now = clock.instant();
		return signedIn(absentEmail).post("/api/v1/me/absences", "{\"delegateUserId\":" + delegateId + ",\"startsAt\":\""
				+ now.minusSeconds(60) + "\",\"endsAt\":\"" + now.plus(Duration.ofDays(1)) + "\"}").expectStatus(201).id();
	}

	@Test
	void administratorsListCompaniesAndUsersOfTheirGroup() {
		ApiClient admin = signedIn("admin@demo.local");
		ApiClient.Response companies = admin.get("/api/v1/admin/companies").expectStatus(200);
		assertThat((List<String>) companies.json("$[*].code")).contains("DEMO-TN-PLT", "DEMO-FR-PLT", "DEMO-CPA");
		assertThat((List<String>) companies.json("$[?(@.code == 'DEMO-TN-PLT')].currency")).containsExactly("TND");

		long tn = companyId("DEMO-TN-PLT");
		ApiClient.Response managers = admin.get("/api/v1/admin/users?companyId=" + tn + "&role=DEPARTMENT_MANAGER")
			.expectStatus(200);
		assertThat((List<String>) managers.json("$[*].email")).contains("manager.tn@demo.local", "manager2.tn@demo.local")
			.doesNotContain("requester.tn@demo.local");
		assertThat((List<List<String>>) managers.json("$[*].roles")).allSatisfy(roles -> assertThat(roles).contains(
				"DEPARTMENT_MANAGER"));
		assertThat((List<String>) admin.get("/api/v1/admin/users?q=Hichem").expectStatus(200).json("$[*].email"))
			.containsExactly("manager2.tn@demo.local");
		admin.get("/api/v1/admin/users?role=NOT_A_ROLE").expectStatus(400);
		signedIn("requester.tn@demo.local").get("/api/v1/admin/companies").expectStatus(403);
		signedIn("requester.tn@demo.local").get("/api/v1/admin/users").expectStatus(403);
	}

	@Test
	void newEndpointsKeepCompaniesApart() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = submit(sami, request("Isolation", "TND", 10, "DEMO-SPARE"));
		long task = pendingTasks(id).get(0);
		ApiClient claire = signedIn("requester.fr@demo.local");
		ApiClient julien = signedIn("manager.fr@demo.local");
		String notFound = withoutInstance(claire.get("/api/v1/requests/999999999/approval").expectStatus(404).body());
		for (ApiClient other : List.of(claire, julien)) {
			assertThat(withoutInstance(other.get("/api/v1/requests/" + id + "/approval").expectStatus(404).body()))
				.isEqualTo(notFound);
			other.get("/api/v1/requests/" + id + "/approval-preview").expectStatus(404);
			other.get("/api/v1/requests/" + id + "/attachments").expectStatus(404);
			other.post("/api/v1/requests/" + id + "/answers", "{\"taskId\":" + task + ",\"answer\":\"x\"}").expectStatus(404);
			other.post("/api/v1/approvals/tasks/" + task + "/approve", "{\"version\":0}").expectStatus(404);
			other.get("/api/v1/approvals/tasks/" + task + "/delegates").expectStatus(404);
		}
		// Non-administrators have no admin endpoints.
		sami.get("/api/v1/admin/approval-rules").expectStatus(403);
		sami.get("/api/v1/admin/exchange-rates").expectStatus(403);
	}

	@Test
	void attachmentsAreSniffedLimitedAndScoped() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient.Response draft = sami.post("/api/v1/requests", json(request("Datasheet", "TND", 10, "DEMO-SPARE")))
			.expectStatus(201);
		long id = draft.id();
		byte[] pdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
		ApiClient.Response uploaded = sami.upload("/api/v1/requests/" + id + "/attachments", "../../etc/ورقة.pdf",
				"application/octet-stream", pdf, null).expectStatus(201);
		assertThat((String) uploaded.json("$.fileName")).isEqualTo("ورقة.pdf");
		assertThat((String) uploaded.json("$.contentType")).isEqualTo("application/pdf");
		// No antivirus in the test profile: the file is recorded as not scanned, never as clean (audit S-1).
		assertThat((String) uploaded.json("$.scanStatus")).isEqualTo("PENDING");
		assertThat((Boolean) uploaded.json("$.downloadable")).isTrue();
		// A script behind a PDF header, under a name Windows would run (audit S-2).
		for (String name : new String[] { "invoice.bat", "invoice.pdf.cmd", "invoice" }) {
			assertThat(sami.upload("/api/v1/requests/" + id + "/attachments", name, "application/pdf",
					"%PDF-1.7\r\n@echo off\r\ncalc.exe\r\n".getBytes(StandardCharsets.US_ASCII), null)
				.expectStatus(415)
				.code()).as(name).isEqualTo("file-type-not-allowed");
		}
		assertThat(sami.upload("/api/v1/requests/" + id + "/attachments", "invoice.pdf", "application/pdf",
				new byte[] { 'M', 'Z', 0, 0, 1 }, null).expectStatus(415).code()).isEqualTo("file-type-not-allowed");
		assertThat(sami.upload("/api/v1/requests/" + id + "/attachments", "logo.txt", "text/plain",
				"<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8), null).expectStatus(415).code())
			.isEqualTo("file-type-not-allowed");
		// Same name again: a new version.
		assertThat(((Number) sami.upload("/api/v1/requests/" + id + "/attachments", "ورقة.pdf", "application/pdf", pdf,
				null).expectStatus(201).json("$.version")).intValue()).isEqualTo(2);

		long attachment = uploaded.id();
		ApiClient.Response download = sami.get("/api/v1/attachments/" + attachment + "/content").expectStatus(200);
		assertThat(download.header("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(download.header("Content-Disposition")).startsWith("attachment");
		signedIn("requester.fr@demo.local").get("/api/v1/attachments/" + attachment + "/content").expectStatus(404);

		ApiClient.Response current = sami.get("/api/v1/requests/" + id).expectStatus(200);
		sami.post("/api/v1/requests/" + id + "/submit", "{\"version\":" + current.version() + "}").expectStatus(200);
		assertThat(sami.upload("/api/v1/requests/" + id + "/attachments", "late.pdf", "application/pdf", pdf, null)
			.expectStatus(409)
			.code()).isEqualTo("invalid-state");
		// The approver can read it once they hold a task.
		signedIn("manager.tn@demo.local").get("/api/v1/attachments/" + attachment + "/content").expectStatus(200);
	}

	// Helpers ---------------------------------------------------------------------------------------------------

	private long submit(ApiClient client, Map<String, Object> body) {
		ApiClient.Response draft = client.post("/api/v1/requests", json(body)).expectStatus(201);
		return client.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();
	}

	private long[] myTask(ApiClient approver, long requestId) {
		ApiClient.Response approval = approver.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		return new long[] { ((Number) approval.json("$.myTask.id")).longValue(),
				((Number) approval.json("$.myTask.version")).longValue() };
	}

	private List<Long> pendingTasks(long requestId) {
		return jdbc.queryForList("SELECT id FROM approval_task WHERE request_id = ? AND status = 'PENDING' ORDER BY id",
				Long.class, requestId);
	}

	/**
	 * The one-tap token of the approver's task e-mail for this request: tokens only exist in sent e-mails (S2CR-01),
	 * so the outbox is sent and the .eml files are read.
	 */
	private String linkToken(long approverId, long requestId) {
		outbox.sendDue();
		String email = jdbc.queryForObject("SELECT email FROM user_account WHERE id = ?", String.class, approverId);
		try (Stream<Path> files = Files.list(Path.of("target/test-mail"))) {
			for (Path file : files.sorted(java.util.Comparator.comparing(Path::toString).reversed()).toList()) {
				String content = htmlOf(file);
				String raw = Files.readString(file, StandardCharsets.ISO_8859_1);
				if (raw.contains("To: " + email) && content.contains("/requests/" + requestId + "\"")) {
					Matcher matcher = LINK.matcher(content);
					if (matcher.find()) {
						return matcher.group(1);
					}
				}
			}
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
		throw new AssertionError("No task e-mail with a one-tap link for user " + approverId);
	}

	private Map<String, Object> request(String title, String currency, Number unitPrice, String categoryCode) {
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", categoryId(categoryCode));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "INTERNATIONAL");
		body.put("currency", currency);
		body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, tn));
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(30).toString());
		body.put("lines", new ArrayList<>(List.of(Map.of("description", title + " item", "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", unitPrice))));
		return body;
	}

	private Map<String, Object> frRequest(String title, Number unitPrice) {
		long fr = companyId("DEMO-FR-PLT");
		Map<String, Object> body = request(title, "TND", unitPrice, "DEMO-SPARE");
		body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, fr));
		return body;
	}

	private long categoryId(String code) {
		return jdbc.queryForObject("SELECT c.id FROM request_category c JOIN organization_group g ON g.id = c.group_id "
				+ "WHERE g.code = 'DEMO-GRP' AND c.code = ?", Long.class, code);
	}

	private static String tokenBody(String token) {
		return "{\"token\":\"" + token + "\"}";
	}

	private static String withoutInstance(String body) {
		return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	/** The decoded text and HTML parts of an .eml file. */
	private static String htmlOf(Path file) {
		try (java.io.InputStream in = Files.newInputStream(file)) {
			jakarta.mail.internet.MimeMessage message = new jakarta.mail.internet.MimeMessage(
					jakarta.mail.Session.getInstance(new java.util.Properties()), in);
			jakarta.mail.Multipart parts = (jakarta.mail.Multipart) message.getContent();
			StringBuilder text = new StringBuilder();
			for (int i = 0; i < parts.getCount(); i++) {
				text.append(parts.getBodyPart(i).getContent());
			}
			return text.toString();
		}
		catch (Exception ex) {
			return "";
		}
	}

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

	private static List<ApiClient.Response> concurrently(List<Callable<ApiClient.Response>> calls) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		try {
			List<Future<ApiClient.Response>> futures = new ArrayList<>();
			for (Callable<ApiClient.Response> call : calls) {
				futures.add(pool.submit(call));
			}
			List<ApiClient.Response> results = new ArrayList<>();
			for (Future<ApiClient.Response> future : futures) {
				results.add(future.get());
			}
			return results;
		}
		finally {
			pool.shutdown();
		}
	}

}
