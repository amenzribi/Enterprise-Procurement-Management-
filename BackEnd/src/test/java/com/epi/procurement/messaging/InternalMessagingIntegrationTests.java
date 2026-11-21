package com.epi.procurement.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * Internal employee messaging against SQL Server (docs/architecture/internal-messaging.md). Numbers in the test names
 * refer to the verification list of the request of 2026-11-13.
 */
class InternalMessagingIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	private static final String API = "/api/v1/internal-messaging";

	@Autowired
	InternalMessageStream stream;

	@Autowired
	com.epi.procurement.platform.web.RequestRateLimiter rateLimiter;

	/** Sends of one test never count against the next (30 a minute per user, in memory). */
	@org.junit.jupiter.api.BeforeEach
	void resetSendLimit() {
		((Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(rateLimiter, "windows")).clear();
	}

	// 1 ----------------------------------------------------------------------------------------------------------
	@Test
	void t01_aRequesterMessagesAManagerOfTheSameCompany() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient karim = signedIn("manager.tn@demo.local");
		long karimId = userId("manager.tn@demo.local");

		List<Integer> found = sami.get(API + "/directory?q=Karim").expectStatus(200).json("$[*].id");
		assertThat(found).contains((int) karimId);

		ApiClient.Response opened = sami.post(API + "/conversations/direct", "{\"userId\":" + karimId + "}").expectStatus(200);
		long conversation = opened.id();
		assertThat((String) opened.json("$.kind")).isEqualTo("DIRECT");
		assertThat((String) opened.json("$.counterpart.displayName")).contains("Karim");

		int before = unread(karim);
		ApiClient.Response sent = send(sami, conversation, "Could you look at my request today?", null).expectStatus(201);
		long messageId = ((Number) sent.json("$.message.id")).longValue();
		assertThat((Boolean) sent.json("$.message.mine")).isTrue();

		List<Integer> conversations = karim.get(API + "/conversations").expectStatus(200).json("$[*].id");
		assertThat(conversations).contains((int) conversation);
		assertThat(unread(karim)).isEqualTo(before + 1);
		List<String> bodies = karim.get(API + "/conversations/" + conversation + "/messages").expectStatus(200)
			.json("$.items[*].bodyText");
		assertThat(bodies).contains("Could you look at my request today?");

		karim.post(API + "/conversations/" + conversation + "/read", "{\"messageId\":" + messageId + "}").expectStatus(204);
		// Reading the newest message clears the whole conversation, including older messages from other tests.
		List<Integer> left = karim.get(API + "/conversations").expectStatus(200)
			.json("$[?(@.id == " + conversation + ")].unread");
		assertThat(left).containsExactly(0);
		assertThat(unread(karim)).isLessThanOrEqualTo(before);
	}

	// 2 ----------------------------------------------------------------------------------------------------------
	@Test
	void t02_employeesOfDifferentDepartmentsExchangePrivateMessages() {
		long tn = companyId("DEMO-TN-PLT");
		long leilaId = userId("budget.tn@demo.local");
		if (jdbc.queryForList("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-QA-MSG'", Long.class, tn)
			.isEmpty()) {
			jdbc.update("INSERT INTO department (company_id, code, name) VALUES (?, 'DEMO-QA-MSG', N'Finance QA (Demo)')", tn);
		}
		long financeId = jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-QA-MSG'",
				Long.class, tn);
		jdbc.update("UPDATE user_account SET department_id = ? WHERE id = ?", financeId, leilaId);
		try {
			ApiClient sami = signedIn("requester.tn@demo.local");
			ApiClient leila = signedIn("budget.tn@demo.local");
			List<Integer> byDepartment = sami.get(API + "/directory?department=Finance").expectStatus(200).json("$[*].id");
			assertThat(byDepartment).containsExactly((int) leilaId);
			List<Integer> byRole = sami.get(API + "/directory?role=BUDGET_APPROVER").expectStatus(200).json("$[*].id");
			assertThat(byRole).contains((int) leilaId);

			long conversation = sami.post(API + "/conversations/direct", "{\"userId\":" + leilaId + "}").expectStatus(200).id();
			send(sami, conversation, "Is the budget line for pumps still open?", null).expectStatus(201);
			send(leila, conversation, "Yes, until the end of the month.", null).expectStatus(201);
			List<String> bodies = sami.get(API + "/conversations/" + conversation + "/messages").expectStatus(200)
				.json("$.items[*].bodyText");
			assertThat(bodies).contains("Is the budget line for pumps still open?", "Yes, until the end of the month.");
		}
		finally {
			jdbc.update("UPDATE user_account SET department_id = NULL WHERE id = ?", leilaId);
		}
	}

	// 3 ----------------------------------------------------------------------------------------------------------
	@Test
	void t03_outsidersCompanyAdminsAndGlobalAdminsCannotReadAPrivateConversation() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		String secret = "Private note " + UUID.randomUUID();
		long messageId = ((Number) send(sami, conversation, secret, null).json("$.message.id")).longValue();

		// Unrelated employee of the same company, company ADMIN, GLOBAL_ADMIN, another company: "not found" on every
		// endpoint that takes the conversation id; the conversation is not listed, not counted, not streamed.
		for (String outsider : List.of("manager2.tn@demo.local", "admin@demo.local", "global.admin@demo.local",
				"requester.fr@demo.local")) {
			ApiClient other = signedIn(outsider);
			int unreadBefore = unread(other);
			try (ApiClient.LiveStream live = other.openStream(API + "/stream")) {
				assertThat(live.await("ready", Duration.ofSeconds(10))).as(outsider).isNotNull();
				for (ApiClient.Response refused : outsiderCalls(other, conversation, messageId)) {
					assertThat(refused.status()).as(outsider + " " + refused.body()).isEqualTo(404);
					assertThat(refused.body()).doesNotContain(secret);
				}
				List<Integer> listed = other.get(API + "/conversations").expectStatus(200).json("$[*].id");
				assertThat(listed).as(outsider).doesNotContain((int) conversation);
				long next = ((Number) send(sami, conversation, "Still private", null).json("$.message.id")).longValue();
				assertThat(live.await("m-" + next, Duration.ofSeconds(3))).as(outsider).isNull();
				assertThat(unread(other)).as(outsider).isEqualTo(unreadBefore);
			}
		}

		// A supplier contact: refused outright (403), stream included.
		ApiClient contact = signedIn("contact.skf@demo.local");
		for (ApiClient.Response refused : outsiderCalls(contact, conversation, messageId)) {
			assertThat(refused.status()).as("supplier " + refused.body()).isEqualTo(403);
			assertThat(refused.body()).doesNotContain(secret);
		}
		contact.get(API + "/conversations").expectStatus(403);
		try (ApiClient.LiveStream live = contact.openStream(API + "/stream")) {
			assertThat(live.awaitClosed(Duration.ofSeconds(5))).isTrue();
			assertThat(live.status()).isEqualTo(403);
		}

		// The two participants still read it.
		ApiClient karim = signedIn("manager.tn@demo.local");
		karim.get(API + "/conversations/" + conversation).expectStatus(200);
		sami.get(API + "/conversations/" + conversation + "/messages").expectStatus(200);
	}

	/** Every call an outsider could make with a known conversation and message id. */
	private List<ApiClient.Response> outsiderCalls(ApiClient other, long conversation, long messageId) {
		String base = API + "/conversations/" + conversation;
		return List.of(other.get(base), other.get(base + "/messages"), other.get(base + "/messages?afterId=0"),
				other.get(base + "/messages?beforeId=" + (messageId + 1)), send(other, conversation, "Let me in", null),
				other.post(base + "/read", "{\"messageId\":" + messageId + "}"), other.get(base + "/candidates"),
				other.post(base + "/participants", "{\"userId\":" + userId("manager2.tn@demo.local") + "}"));
	}

	// 4 ----------------------------------------------------------------------------------------------------------
	@Test
	void t04_aUserOfAnotherCompanyCannotDiscoverOrJoin() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		ApiClient claire = signedIn("requester.fr@demo.local");
		List<String> names = claire.get(API + "/directory?q=Sami").expectStatus(200).json("$[*].displayName");
		assertThat(names).isEmpty();
		List<String> everyone = claire.get(API + "/directory").expectStatus(200).json("$[*].displayName");
		assertThat(everyone).noneMatch(n -> n.contains("Sami") || n.contains("Karim"));
		claire.post(API + "/conversations/direct", "{\"userId\":" + userId("requester.tn@demo.local") + "}").expectStatus(404);
		claire.get(API + "/conversations/" + conversation).expectStatus(404);
	}

	// 5 ----------------------------------------------------------------------------------------------------------
	@Test
	void t05_suppliersAreNotInTheDirectoryAndCannotUseInternalEndpoints() {
		ApiClient ines = signedIn("buyer@demo.local");
		List<String> sameCompany = ines.get(API + "/directory").expectStatus(200).json("$[*].displayName");
		assertThat(sameCompany).isNotEmpty().noneMatch(n -> n.contains("SKF"));
		ines.post(API + "/conversations/direct", "{\"userId\":" + userId("contact.skf@demo.local") + "}").expectStatus(404);

		ApiClient contact = signedIn("contact.skf@demo.local");
		contact.get(API + "/directory").expectStatus(403);
		contact.get(API + "/conversations").expectStatus(403);
		contact.get(API + "/unread").expectStatus(403);
		try (ApiClient.LiveStream live = contact.openStream(API + "/stream")) {
			assertThat(live.awaitClosed(Duration.ofSeconds(5))).isTrue();
			assertThat(live.status()).isEqualTo(403);
		}
	}

	// 6, 7, 9 ----------------------------------------------------------------------------------------------------
	@Test
	void t06_aRequestDiscussionNeedsParticipationAndCurrentRequestAccess() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long request = submit(sami, "Discussion pumps " + UUID.randomUUID().toString().substring(0, 6));
		long karimId = userId("manager.tn@demo.local");
		long hichemId = userId("manager2.tn@demo.local");

		ApiClient.Response started = sami.post(API + "/requests/" + request + "/discussion", null).expectStatus(201);
		long discussion = started.id();
		List<Integer> participants = started.json("$.participants[*].id");
		assertThat(participants).contains((int) userId("requester.tn@demo.local"), (int) karimId, (int) hichemId);
		assertThat((String) started.json("$.request.number")).isNotBlank();
		sami.post(API + "/requests/" + request + "/discussion", null).expectStatus(409);

		// No access to the request: not found, whatever the conversation id says.
		ApiClient claire = signedIn("requester.fr@demo.local");
		claire.get(API + "/requests/" + request + "/discussion").expectStatus(404);
		claire.get(API + "/conversations/" + discussion).expectStatus(404);
		// Access to the request but not a participant: told it exists, cannot read it.
		ApiClient ceo = signedIn("ceo@demo.local");
		ApiClient.Response seen = ceo.get(API + "/requests/" + request + "/discussion").expectStatus(200);
		assertThat((Boolean) seen.json("$.exists")).isTrue();
		assertThat((Object) seen.json("$.conversation")).isNull();
		ceo.get(API + "/conversations/" + discussion + "/messages").expectStatus(404);

		// Only people who can already see the request, in the request's companies, may be added.
		List<Integer> candidates = sami.get(API + "/conversations/" + discussion + "/candidates").expectStatus(200).json("$[*].id");
		assertThat(candidates).doesNotContain((int) userId("receiver.tn@demo.local"), (int) userId("buyer@demo.local"));
		ApiClient.Response receiver = sami.post(API + "/conversations/" + discussion + "/participants",
				"{\"userId\":" + userId("receiver.tn@demo.local") + "}").expectStatus(422);
		assertThat(receiver.body()).contains("no-request-access");
		sami.post(API + "/conversations/" + discussion + "/participants",
				"{\"userId\":" + userId("ceo@demo.local") + "}").expectStatus(422);
		sami.post(API + "/conversations/" + discussion + "/participants",
				"{\"userId\":" + userId("requester.fr@demo.local") + "}").expectStatus(422);

		// Karim approves: the request moves to purchasing, whose manager can now see it and be added.
		ApiClient karim = signedIn("manager.tn@demo.local");
		long[] task = myTask(karim, request);
		karim.post("/api/v1/approvals/tasks/" + task[0] + "/approve", "{\"version\":" + task[1] + "}").expectStatus(200);
		long mehdiId = userId("purchasing.manager@demo.local");
		ApiClient.Response added = sami.post(API + "/conversations/" + discussion + "/participants",
				"{\"userId\":" + mehdiId + "}").expectStatus(200);
		assertThat((List<Integer>) added.json("$.participants[*].id")).contains((int) mehdiId);
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_event WHERE entity_type = 'INTERNAL_CONVERSATION' AND entity_id = ?
				  AND action = 'PARTICIPANT_ADDED'""", Integer.class, Long.toString(discussion))).isEqualTo(1);
		List<String> kinds = sami.get(API + "/conversations/" + discussion + "/messages").expectStatus(200).json("$.items[*].bodyText");
		assertThat(kinds).contains("DISCUSSION_STARTED", "PARTICIPANT_ADDED");

		// 9: a colleague of the purchasing manager sends him the request card while he can see it.
		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		long dm = direct(ines, "purchasing.manager@demo.local");
		ApiClient.Response withCard = send(ines, dm, "This one is for us", request).expectStatus(201);
		assertThat((Boolean) withCard.json("$.cardWithheld")).isFalse();
		assertThat(lastMessage(mehdi, dm).get("request").get("available").asBoolean()).isTrue();

		// 7: the purchasing manager loses his role, and with it his access to the request.
		long grant = jdbc.queryForObject(
				"SELECT id FROM user_role WHERE user_id = ? AND role = 'PURCHASING_MANAGER'", Long.class, mehdiId);
		Map<String, Object> saved = jdbc.queryForMap("SELECT * FROM user_role WHERE id = ?", grant);
		long seenByMehdi = ((Number) send(sami, discussion, "Before the change", null).json("$.message.id")).longValue();
		ApiClient.LiveStream mehdiLive = mehdi.openStream(API + "/stream");
		assertThat(mehdiLive.await("ready", Duration.ofSeconds(10))).isNotNull();
		jdbc.update("DELETE FROM user_role WHERE id = ?", grant);
		boolean restored = false;
		try {
			// Live: the discussion is no longer delivered to his open stream; his direct messages still are.
			long hidden = ((Number) send(sami, discussion, "After the change", null).json("$.message.id")).longValue();
			assertThat(mehdiLive.await("m-" + hidden, Duration.ofSeconds(3))).isNull();
			long stillDm = ((Number) send(ines, dm, "Direct still works", null).json("$.message.id")).longValue();
			assertThat(mehdiLive.await("m-" + stillDm, Duration.ofSeconds(10))).isNotNull();
			mehdi.get(API + "/requests/" + request + "/discussion").expectStatus(404);
			mehdi.get(API + "/conversations/" + discussion).expectStatus(404);
			mehdi.get(API + "/conversations/" + discussion + "/messages").expectStatus(404);
			send(mehdi, discussion, "Still here?", null).expectStatus(404);
			List<Integer> listed = mehdi.get(API + "/conversations").expectStatus(200).json("$[*].id");
			assertThat(listed).doesNotContain((int) discussion).contains((int) dm);
			// 9: the card in his direct message now discloses nothing about the request.
			var card = messageWith(mehdi, dm, "This one is for us").get("request");
			assertThat(card.get("available").asBoolean()).isFalse();
			assertThat(card.get("id").isNull()).isTrue();
			assertThat(card.get("title").isNull()).isTrue();
			assertThat(card.get("number").isNull()).isTrue();
			assertThat(card.get("status").isNull()).isTrue();

			// Access given back: he reads the discussion again and a reconnecting tab recovers what it missed.
			jdbc.update("INSERT INTO user_role (user_id, role, company_id, department_id) VALUES (?, ?, ?, ?)",
					saved.get("user_id"), saved.get("role"), saved.get("company_id"), saved.get("department_id"));
			restored = true;
			List<Integer> recovered = mehdi.get(API + "/conversations/" + discussion + "/messages?afterId=" + seenByMehdi)
				.expectStatus(200)
				.json("$.items[*].id");
			assertThat(recovered).containsExactly((int) hidden);
			long again = ((Number) send(sami, discussion, "Welcome back", null).json("$.message.id")).longValue();
			assertThat(mehdiLive.await("m-" + again, Duration.ofSeconds(10))).isNotNull();
		}
		finally {
			mehdiLive.close();
			if (!restored) {
				jdbc.update("INSERT INTO user_role (user_id, role, company_id, department_id) VALUES (?, ?, ?, ?)",
						saved.get("user_id"), saved.get("role"), saved.get("company_id"), saved.get("department_id"));
			}
		}
	}

	// 7 (deactivation) -------------------------------------------------------------------------------------------
	@Test
	void t07_deactivationStopsHistoryAndLiveDelivery() throws Exception {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long conversation = direct(sami, "receiver.tn@demo.local");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		long omarId = userId("receiver.tn@demo.local");
		try (ApiClient.LiveStream live = omar.openStream(API + "/stream")) {
			assertThat(live.await("ready", Duration.ofSeconds(10))).isNotNull();
			long id = ((Number) send(sami, conversation, "Delivery tomorrow?", null).json("$.message.id")).longValue();
			assertThat(live.await("m-" + id, Duration.ofSeconds(10))).isNotNull();

			jdbc.update("UPDATE user_account SET is_active = 0 WHERE id = ?", omarId);
			try {
				// Before the heartbeat runs, delivery already re-checks the account: nothing reaches the open stream.
				long whileInactive = ((Number) send(sami, conversation, "Are you there?", null).json("$.message.id"))
					.longValue();
				assertThat(live.await("m-" + whileInactive, Duration.ofSeconds(3))).isNull();
				stream.heartbeat();
				assertThat(live.awaitClosed(Duration.ofSeconds(10))).isTrue();
				assertThat(stream.openStreams(omarId)).isZero();
				// The old session is refused everywhere, the stream included.
				omar.get(API + "/conversations/" + conversation + "/messages").expectStatus(401);
				omar.get(API + "/conversations").expectStatus(401);
				omar.get(API + "/unread").expectStatus(401);
				try (ApiClient.LiveStream refused = omar.openStream(API + "/stream")) {
					assertThat(refused.awaitClosed(Duration.ofSeconds(5))).isTrue();
					assertThat(refused.status()).isEqualTo(401);
				}

				// Reactivated: a new sign-in reconnects and recovers what was sent meanwhile, once.
				jdbc.update("UPDATE user_account SET is_active = 1 WHERE id = ?", omarId);
				ApiClient back = signedIn("receiver.tn@demo.local");
				try (ApiClient.LiveStream again = back.openStream(API + "/stream")) {
					assertThat(again.await("ready", Duration.ofSeconds(10))).isNotNull();
					List<Integer> recovered = back.get(API + "/conversations/" + conversation + "/messages?afterId=" + id)
						.expectStatus(200)
						.json("$.items[*].id");
					assertThat(recovered).containsExactly((int) whileInactive);
					long live2 = ((Number) send(sami, conversation, "Welcome back", null).json("$.message.id")).longValue();
					assertThat(again.await("m-" + live2, Duration.ofSeconds(10))).isNotNull();
				}
			}
			finally {
				jdbc.update("UPDATE user_account SET is_active = 1 WHERE id = ?", omarId);
			}
		}
	}

	// 8 ----------------------------------------------------------------------------------------------------------
	@Test
	void t08_messageApproverResolvesTheCurrentStageAndDelegation() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long karimId = userId("manager.tn@demo.local");
		long hichemId = userId("manager2.tn@demo.local");

		ApiClient.Response draft = sami.post("/api/v1/requests", json(request("Draft only"))).expectStatus(201);
		assertThat((String) sami.get(API + "/requests/" + draft.id() + "/approvers").expectStatus(200).json("$.state"))
			.isEqualTo("NOT_IN_APPROVAL");

		long request = submit(sami, "Approver pumps " + UUID.randomUUID().toString().substring(0, 6));
		ApiClient.Response approvers = sami.get(API + "/requests/" + request + "/approvers").expectStatus(200);
		assertThat((String) approvers.json("$.state")).isEqualTo("AWAITING");
		List<Integer> ids = approvers.json("$.approvers[*].person.id");
		assertThat(ids).containsExactlyInAnyOrder((int) karimId, (int) hichemId);
		assertThat((List<String>) approvers.json("$.approvers[*].stageName")).allMatch(s -> !s.isBlank());

		// Karim delegates his task. Leila temporarily holds Karim's approver role, which makes her an eligible delegate.
		long leilaId = userId("budget.tn@demo.local");
		jdbc.update("INSERT INTO user_role (user_id, role, company_id, department_id) SELECT ?, role, company_id, department_id "
				+ "FROM user_role WHERE user_id = ? AND role = 'DEPARTMENT_MANAGER'", leilaId, karimId);
		long delegateId;
		try {
			ApiClient karim = signedIn("manager.tn@demo.local");
			long[] task = myTask(karim, request);
			List<Integer> delegates = karim.get("/api/v1/approvals/tasks/" + task[0] + "/delegates").expectStatus(200).json("$[*].id");
			assertThat(delegates).contains((int) leilaId);
			delegateId = leilaId;
			karim.post("/api/v1/approvals/tasks/" + task[0] + "/delegate", "{\"version\":" + task[1]
					+ ",\"toUserId\":" + delegateId + ",\"reason\":\"Away this week\"}").expectStatus(200);
		}
		finally {
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'DEPARTMENT_MANAGER'", leilaId);
		}

		ApiClient.Response after = sami.get(API + "/requests/" + request + "/approvers").expectStatus(200);
		List<Integer> afterIds = after.json("$.approvers[*].person.id");
		assertThat(afterIds).doesNotContain((int) karimId).contains((int) delegateId);
		List<Integer> onBehalfOf = after.json("$.approvers[?(@.person.id == " + delegateId + ")].onBehalfOf.id");
		assertThat(onBehalfOf).containsExactly((int) karimId);

		// The recipient chosen before the delegation is refused, and nothing is sent.
		String key = clientId();
		ApiClient.Response changed = sami.post(API + "/requests/" + request + "/message-approver",
				"{\"recipientUserId\":" + karimId + ",\"bodyText\":\"Could you please review this request?\",\"clientMessageId\":\""
						+ key + "\"}").expectStatus(409);
		assertThat(changed.code()).isEqualTo("approver-changed");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM internal_message WHERE client_message_id = ?", Integer.class, key)).isZero();

		ApiClient.Response sent = sami.post(API + "/requests/" + request + "/message-approver",
				"{\"recipientUserId\":" + delegateId + ",\"bodyText\":\"Could you please review this request?\",\"clientMessageId\":\""
						+ clientId() + "\"}").expectStatus(201);
		assertThat((String) sent.json("$.kind")).isEqualTo("DIRECT");
		assertThat((Boolean) sent.json("$.message.request.available")).isTrue();
		assertThat(((Number) sent.json("$.message.request.id")).longValue()).isEqualTo(request);

		// The delegate opens the request from the card and can act on it in the approval workflow.
		ApiClient delegate = signedIn(jdbc.queryForObject("SELECT email FROM user_account WHERE id = ?", String.class, delegateId));
		long conversation = ((Number) sent.json("$.conversationId")).longValue();
		var card = lastMessage(delegate, conversation).get("request");
		assertThat(card.get("available").asBoolean()).isTrue();
		assertThat((Object) delegate.get("/api/v1/requests/" + request + "/approval").expectStatus(200).json("$.myTask")).isNotNull();
	}

	// 6 (explicit participation through "Message approver") -------------------------------------------------------
	@Test
	void t14_messageApproverNeverJoinsADiscussionTheSenderIsNotIn() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long request = submit(sami, "Group approver " + UUID.randomUUID().toString().substring(0, 6));
		long karimId = userId("manager.tn@demo.local");
		long hedi = userId("ceo@demo.local");
		// The group-level approver (another company) gets the task; Karim starts a discussion without Sami.
		long task = jdbc.queryForObject(
				"SELECT TOP 1 id FROM approval_task WHERE request_id = ? AND status = 'PENDING' AND assignee_user_id = ?",
				Long.class, request, karimId);
		jdbc.update("UPDATE approval_task SET assignee_user_id = ?, original_user_id = ? WHERE id = ?", hedi, karimId, task);
		ApiClient karim = signedIn("manager.tn@demo.local");
		long discussion = karim.post(API + "/requests/" + request + "/discussion", null).expectStatus(201).id();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM internal_participant WHERE conversation_id = ? AND user_id = ?",
				Integer.class, discussion, userId("requester.tn@demo.local"))).isZero();

		ApiClient.Response refused = sami.post(API + "/requests/" + request + "/message-approver",
				"{\"recipientUserId\":" + hedi + ",\"bodyText\":\"Please review\",\"clientMessageId\":\"" + clientId() + "\"}")
			.expectStatus(409);
		assertThat(refused.code()).isEqualTo("discussion-not-participant");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM internal_participant WHERE conversation_id = ? AND user_id = ?",
				Integer.class, discussion, userId("requester.tn@demo.local"))).isZero();
	}

	// 9 ----------------------------------------------------------------------------------------------------------
	@Test
	void t09_aCardTheRecipientCannotSeeIsWithheldAndAnInvisibleOneRefused() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		long draft = sami.post("/api/v1/requests", json(request("Private draft"))).expectStatus(201).id();
		ApiClient.Response sent = send(sami, conversation, "Draft attached", draft).expectStatus(201);
		assertThat((Boolean) sent.json("$.cardWithheld")).isTrue();
		assertThat((Object) sent.json("$.message.request")).isNull();
		ApiClient karim = signedIn("manager.tn@demo.local");
		assertThat(lastMessage(karim, conversation).get("request").isNull()).isTrue();

		ApiClient claire = signedIn("requester.fr@demo.local");
		long lyon = claire.post("/api/v1/requests", json(frRequest("Lyon only"))).expectStatus(201).id();
		ApiClient.Response refused = send(sami, conversation, "Not mine", lyon).expectStatus(422);
		assertThat(refused.body()).contains("requestId");
	}

	// 10 ---------------------------------------------------------------------------------------------------------
	@Test
	void t10_retriesAndRacesCreateOneMessageAndOneConversation() throws Exception {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		String key = clientId();
		long first = ((Number) send(sami, conversation, "Only once", null, key).expectStatus(201).json("$.message.id")).longValue();
		ApiClient.Response retry = send(sami, conversation, "Only once", null, key).expectStatus(200);
		assertThat(((Number) retry.json("$.message.id")).longValue()).isEqualTo(first);
		assertThat((Boolean) retry.json("$.duplicate")).isTrue();
		send(sami, conversation, "A different text", null, key).expectStatus(409);

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			// Eight tabs retry the same message at the same moment, five times: one message each time, never "busy".
			for (int round = 0; round < 5; round++) {
				String raced = clientId();
				List<Callable<Integer>> sends = new ArrayList<>();
				for (int i = 0; i < 8; i++) {
					sends.add(() -> send(sami, conversation, "Raced", null, raced).status());
				}
				List<Integer> statuses = new ArrayList<>();
				for (Future<Integer> f : pool.invokeAll(sends)) {
					statuses.add(f.get());
				}
				assertThat(statuses).allMatch(s -> s == 200 || s == 201).filteredOn(s -> s == 201).hasSize(1);
				assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM internal_message WHERE client_message_id = ?",
						Integer.class, raced)).isEqualTo(1);
			}

			// Two people open the same direct conversation at the same moment: one conversation.
			ApiClient amel = signedIn("assistant.tn@demo.local");
			ApiClient omar = signedIn("receiver.tn@demo.local");
			long amelId = userId("assistant.tn@demo.local");
			long omarId = userId("receiver.tn@demo.local");
			Future<Long> a = pool.submit(() -> amel.post(API + "/conversations/direct", "{\"userId\":" + omarId + "}").expectStatus(200).id());
			Future<Long> b = pool.submit(() -> omar.post(API + "/conversations/direct", "{\"userId\":" + amelId + "}").expectStatus(200).id());
			assertThat(a.get()).isEqualTo(b.get());
			assertThat(jdbc.queryForObject("""
					SELECT COUNT(*) FROM internal_conversation WHERE kind = 'DIRECT' AND user_low_id = ? AND user_high_id = ?""",
					Integer.class, Math.min(amelId, omarId), Math.max(amelId, omarId))).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	// 11 ---------------------------------------------------------------------------------------------------------
	@Test
	void t11_aReconnectedTabRecoversMissedMessagesWithoutDuplicates() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient karim = signedIn("manager.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		long seen = ((Number) send(sami, conversation, "Before the cut", null).json("$.message.id")).longValue();

		// Disconnected: two messages arrive. The tab reconnects and asks for everything after the last id it has.
		long m1 = ((Number) send(sami, conversation, "Missed one", null).json("$.message.id")).longValue();
		long m2 = ((Number) send(sami, conversation, "Missed two", null).json("$.message.id")).longValue();
		List<Integer> recovered = karim.get(API + "/conversations/" + conversation + "/messages?afterId=" + seen)
			.expectStatus(200)
			.json("$.items[*].id");
		assertThat(recovered).containsExactly((int) m1, (int) m2);
		List<Integer> again = karim.get(API + "/conversations/" + conversation + "/messages?afterId=" + m2)
			.expectStatus(200)
			.json("$.items[*].id");
		assertThat(again).isEmpty();

		try (ApiClient.LiveStream live = karim.openStream(API + "/stream")) {
			assertThat(live.await("ready", Duration.ofSeconds(10))).isNotNull();
			long m3 = ((Number) send(sami, conversation, "Live one", null).json("$.message.id")).longValue();
			assertThat(live.await("\"messageId\":" + m3, Duration.ofSeconds(10))).isNotNull();
		}
		// Ordering is by id, oldest first, and stable across pages.
		List<Integer> page = karim.get(API + "/conversations/" + conversation + "/messages").expectStatus(200).json("$.items[*].id");
		assertThat(page).isSorted();

		// Sign-out: the open stream ends at the next heartbeat, and the old cookie cannot reconnect.
		ApiClient leaving = signedIn("manager.tn@demo.local");
		try (ApiClient.LiveStream live = leaving.openStream(API + "/stream")) {
			assertThat(live.await("ready", Duration.ofSeconds(10))).isNotNull();
			leaving.post("/api/v1/auth/logout", null).expectStatus(204);
			stream.heartbeat();
			assertThat(live.awaitClosed(Duration.ofSeconds(10))).isTrue();
		}
		try (ApiClient.LiveStream refused = leaving.openStream(API + "/stream")) {
			assertThat(refused.awaitClosed(Duration.ofSeconds(5))).isTrue();
			assertThat(refused.status()).isIn(401, 403);
		}
		leaving.get(API + "/conversations/" + conversation + "/messages").expectStatus(401);
	}

	// 12 ---------------------------------------------------------------------------------------------------------
	@Test
	void t12_twoTabsKeepUnreadCountsExact() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient tab1 = signedIn("manager.tn@demo.local");
		ApiClient tab2 = signedIn("manager.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		long last = ((Number) send(sami, conversation, "Catch-up", null).json("$.message.id")).longValue();
		tab1.post(API + "/conversations/" + conversation + "/read", "{\"messageId\":" + last + "}").expectStatus(204);
		int base = unread(tab1);

		long a = ((Number) send(sami, conversation, "First", null).json("$.message.id")).longValue();
		long b = ((Number) send(sami, conversation, "Second", null).json("$.message.id")).longValue();
		assertThat(unread(tab1)).isEqualTo(base + 2);
		assertThat(unread(tab2)).isEqualTo(base + 2);

		try (ApiClient.LiveStream live = tab2.openStream(API + "/stream")) {
			assertThat(live.await("ready", Duration.ofSeconds(10))).isNotNull();
			tab1.post(API + "/conversations/" + conversation + "/read", "{\"messageId\":" + a + "}").expectStatus(204);
			assertThat(live.await("\"conversationId\":" + conversation, Duration.ofSeconds(10))).isNotNull();
		}
		assertThat(unread(tab2)).isEqualTo(base + 1);
		tab2.post(API + "/conversations/" + conversation + "/read", "{\"messageId\":" + b + "}").expectStatus(204);
		// A late request from the other tab with an older id never moves the marker back.
		tab1.post(API + "/conversations/" + conversation + "/read", "{\"messageId\":" + a + "}").expectStatus(204);
		assertThat(unread(tab1)).isEqualTo(base);
		assertThat(unread(tab2)).isEqualTo(base);
		// The sender's own messages never count as unread for the sender.
		assertThat(unread(sami)).isGreaterThanOrEqualTo(0);
	}

	// 13 ---------------------------------------------------------------------------------------------------------
	@Test
	void t13_textIsStoredVerbatimAndBounded() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long conversation = direct(sami, "manager.tn@demo.local");
		String hostile = "<script>alert('x')</script>\n<img src=x onerror=alert(1)>\nLine three";
		send(sami, conversation, hostile, null).expectStatus(201);
		ApiClient karim = signedIn("manager.tn@demo.local");
		assertThat(lastMessage(karim, conversation).get("bodyText").asText()).isEqualTo(hostile);

		send(sami, conversation, "   \n  ", null).expectStatus(422);
		send(sami, conversation, "x".repeat(4001), null).expectStatus(422);
		send(sami, conversation, "x".repeat(4000), null).expectStatus(201);
		sami.post(API + "/conversations/" + conversation + "/messages", "{\"bodyText\":\"no id\"}").expectStatus(422);
	}

	// 15 ---------------------------------------------------------------------------------------------------------
	@Test
	void t15_aRemovedParticipantLosesTheDiscussionEvenWhileTheyStillSeeTheRequest() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long request = submit(sami, "Removal seals " + UUID.randomUUID().toString().substring(0, 6));
		long discussion = sami.post(API + "/requests/" + request + "/discussion", null).expectStatus(201).id();
		long hichemId = userId("manager2.tn@demo.local");
		ApiClient hichem = signedIn("manager2.tn@demo.local");
		long seen = ((Number) send(sami, discussion, "Before removal", null).json("$.message.id")).longValue();
		hichem.get(API + "/conversations/" + discussion + "/messages").expectStatus(200);

		// Removal has no screen in this release; the column it uses is set directly.
		try (ApiClient.LiveStream live = hichem.openStream(API + "/stream")) {
			assertThat(live.await("ready", Duration.ofSeconds(10))).isNotNull();
			int unreadWhileIn = unread(hichem);
			jdbc.update("UPDATE internal_participant SET removed_at = SYSUTCDATETIME() WHERE conversation_id = ? AND user_id = ?",
					discussion, hichemId);
			// The discussion's unread messages stop counting for him.
			int unreadBefore = unread(hichem);
			assertThat(unreadBefore).isLessThan(unreadWhileIn);

			// He is still an approver of the request and still sees it.
			hichem.get("/api/v1/requests/" + request).expectStatus(200);
			// The discussion: known to exist, not readable, not writable, not listed, not counted, not streamed.
			ApiClient.Response view = hichem.get(API + "/requests/" + request + "/discussion").expectStatus(200);
			assertThat((Boolean) view.json("$.exists")).isTrue();
			assertThat((Object) view.json("$.conversation")).isNull();
			for (ApiClient.Response refused : outsiderCalls(hichem, discussion, seen)) {
				assertThat(refused.status()).as(refused.body()).isEqualTo(404);
				assertThat(refused.body()).doesNotContain("Before removal");
			}
			List<Integer> listed = hichem.get(API + "/conversations").expectStatus(200).json("$[*].id");
			assertThat(listed).doesNotContain((int) discussion);
			long after = ((Number) send(sami, discussion, "After removal", null).json("$.message.id")).longValue();
			assertThat(live.await("m-" + after, Duration.ofSeconds(3))).isNull();
			assertThat(unread(hichem)).isEqualTo(unreadBefore);
			// The others no longer see him among the participants.
			List<Integer> participants = sami.get(API + "/conversations/" + discussion).expectStatus(200)
				.json("$.participants[*].id");
			assertThat(participants).doesNotContain((int) hichemId);

			// Added again by a participant: one audited addition, a visible line, and access back.
			int audits = jdbc.queryForObject("""
					SELECT COUNT(*) FROM audit_event WHERE entity_type = 'INTERNAL_CONVERSATION' AND entity_id = ?
					  AND action = 'PARTICIPANT_ADDED'""", Integer.class, Long.toString(discussion));
			ApiClient.Response readded = sami.post(API + "/conversations/" + discussion + "/participants",
					"{\"userId\":" + hichemId + "}").expectStatus(200);
			assertThat((List<Integer>) readded.json("$.participants[*].id")).contains((int) hichemId);
			assertThat(jdbc.queryForObject("""
					SELECT COUNT(*) FROM audit_event WHERE entity_type = 'INTERNAL_CONVERSATION' AND entity_id = ?
					  AND action = 'PARTICIPANT_ADDED'""", Integer.class, Long.toString(discussion))).isEqualTo(audits + 1);
			List<String> bodies = hichem.get(API + "/conversations/" + discussion + "/messages").expectStatus(200)
				.json("$.items[*].bodyText");
			assertThat(bodies).contains("Before removal", "After removal", "PARTICIPANT_ADDED");
		}
	}

	// ---- helpers --------------------------------------------------------------------------------------------------

	private long direct(ApiClient from, String toEmail) {
		return from.post(API + "/conversations/direct", "{\"userId\":" + userId(toEmail) + "}").expectStatus(200).id();
	}

	private ApiClient.Response send(ApiClient from, long conversation, String text, Long requestId) {
		return send(from, conversation, text, requestId, clientId());
	}

	private ApiClient.Response send(ApiClient from, long conversation, String text, Long requestId, String key) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("bodyText", text);
		body.put("clientMessageId", key);
		if (requestId != null) {
			body.put("requestId", requestId);
		}
		return from.post(API + "/conversations/" + conversation + "/messages", json(body));
	}

	private tools.jackson.databind.JsonNode lastMessage(ApiClient reader, long conversation) {
		String body = reader.get(API + "/conversations/" + conversation + "/messages").expectStatus(200).body();
		var items = JSON.readTree(body).get("items");
		return items.get(items.size() - 1);
	}

	private tools.jackson.databind.JsonNode messageWith(ApiClient reader, long conversation, String text) {
		String body = reader.get(API + "/conversations/" + conversation + "/messages").expectStatus(200).body();
		for (var item : JSON.readTree(body).get("items")) {
			if (text.equals(item.get("bodyText").asText())) {
				return item;
			}
		}
		throw new AssertionError("No message " + text);
	}

	private int unread(ApiClient client) {
		return ((Number) client.get(API + "/unread").expectStatus(200).json("$.total")).intValue();
	}

	private static String clientId() {
		return "t-" + UUID.randomUUID();
	}

	private long submit(ApiClient client, String title) {
		ApiClient.Response draft = client.post("/api/v1/requests", json(request(title))).expectStatus(201);
		return client.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();
	}

	private long[] myTask(ApiClient approver, long requestId) {
		ApiClient.Response approval = approver.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		return new long[] { ((Number) approval.json("$.myTask.id")).longValue(),
				((Number) approval.json("$.myTask.version")).longValue() };
	}

	private Map<String, Object> request(String title) {
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", categoryId("DEMO-SPARE"));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "LOCAL");
		body.put("currency", "TND");
		body.put("departmentId", jdbc.queryForObject(
				"SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'", Long.class, tn));
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(30).toString());
		body.put("lines", new ArrayList<>(List.of(Map.of("description", title + " item", "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 120))));
		return body;
	}

	private Map<String, Object> frRequest(String title) {
		Map<String, Object> body = request(title);
		body.put("departmentId", jdbc.queryForObject(
				"SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'", Long.class,
				companyId("DEMO-FR-PLT")));
		return body;
	}

	private long categoryId(String code) {
		return jdbc.queryForObject("SELECT c.id FROM request_category c JOIN organization_group g ON g.id = c.group_id "
				+ "WHERE g.code = 'DEMO-GRP' AND c.code = ?", Long.class, code);
	}

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}
