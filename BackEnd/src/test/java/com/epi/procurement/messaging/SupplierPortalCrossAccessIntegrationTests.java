package com.epi.procurement.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sprint 2 security regression: a supplier contact reaches the conversations of its own supplier and nothing of
 * another supplier, on every supplier-portal conversation endpoint, with valid requests (so a validation error can
 * never stand in for a refusal). The fixtures are created here (one RFQ, one conversation per supplier), so the result
 * does not depend on the order in which test classes run.
 */
class SupplierPortalCrossAccessIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	private static final String PORTAL = "/api/v1/supplier-portal/conversations/";

	private record Thread(long conversationId, long messageId, String text) {
	}

	@Test
	void aSupplierContactReachesItsOwnSuppliersConversationAndNothingOfAnotherSupplier() {
		long skf = supplier("DEMO-SKF");
		// Another supplier the buyer may invite (shared), with no relation to DEMO-SKF, whose contact probes.
		String otherCode = "S2-XACCESS-" + UUID.randomUUID().toString().substring(0, 8);
		long seals = ((Number) signedIn("purchasing.manager@demo.local").post("/api/v1/suppliers", "{\"code\":\"" + otherCode
				+ "\",\"legalName\":\"Cross-access supplier (Demo)\",\"countryCode\":\"TN\",\"visibility\":\"SHARED\"}")
			.expectStatus(201)
			.json("$.id")).longValue();
		ApiClient buyer = signedIn("buyer@demo.local");
		long rfq = rfqInviting(buyer, skf, seals);
		String marker = UUID.randomUUID().toString().substring(0, 8);
		Thread own = open(buyer, rfq, skf, "SKF lead time question " + marker);
		Thread foreign = open(buyer, rfq, seals, "Seals confidential price 4711 " + marker);

		ApiClient contact = signedIn("contact.skf@demo.local");
		long contactUser = userId("contact.skf@demo.local");

		// Control: the same endpoints answer for the contact's own supplier, so a 404 below is a refusal.
		assertThat(contact.get(PORTAL + own.conversationId()).expectStatus(200).body()).contains("\"id\":" + own.conversationId());
		assertThat(contact.get(PORTAL + own.conversationId() + "/messages").expectStatus(200).body()).contains(own.text());
		contact.post(PORTAL + own.conversationId() + "/messages", validMessage("Reply from SKF " + marker)).expectStatus(201);
		contact.post(PORTAL + own.conversationId() + "/read", "{\"messageId\":" + own.messageId() + "}").expectStatus(204);
		String list = contact.get("/api/v1/supplier-portal/conversations").expectStatus(200).body();
		assertThat(list).contains("\"id\":" + own.conversationId()).doesNotContain("\"id\":" + foreign.conversationId())
			.doesNotContain(foreign.text())
			.doesNotContain(otherCode);

		// The other supplier's conversation: every operation refused with 404, valid bodies included.
		long messagesBefore = count("SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ?",
				foreign.conversationId());
		long participantsBefore = count("SELECT COUNT(*) FROM conversation_participant WHERE conversation_id = ?",
				foreign.conversationId());
		String unknown = contact.get(PORTAL + 999_999_001).expectStatus(404).body();
		List<ApiClient.Response> refusals = List.of(
				contact.get(PORTAL + foreign.conversationId()).expectStatus(404),
				contact.get(PORTAL + foreign.conversationId() + "/messages").expectStatus(404),
				contact.post(PORTAL + foreign.conversationId() + "/messages", validMessage("Intrusion " + marker))
					.expectStatus(404),
				contact.post(PORTAL + foreign.conversationId() + "/read", "{\"messageId\":" + foreign.messageId() + "}")
					.expectStatus(404));
		for (ApiClient.Response refusal : refusals) {
			assertThat(refusal.code()).isEqualTo("not-found");
			assertThat(refusal.body()).doesNotContain(foreign.text()).doesNotContain(otherCode);
		}
		// Indistinguishable from an id that does not exist (apart from the path the caller sent).
		assertThat(withoutInstance(refusals.get(0).body())).isEqualTo(withoutInstance(unknown));

		// Nothing changed in the other supplier's conversation.
		assertThat(count("SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ?", foreign.conversationId()))
			.isEqualTo(messagesBefore);
		assertThat(count("SELECT COUNT(*) FROM conversation_message WHERE conversation_id = ? AND body_text LIKE ?",
				foreign.conversationId(), "Intrusion " + marker)).isZero();
		assertThat(count("SELECT COUNT(*) FROM conversation_participant WHERE conversation_id = ?", foreign.conversationId()))
			.isEqualTo(participantsBefore);
		assertThat(count("SELECT COUNT(*) FROM conversation_participant WHERE conversation_id = ? AND user_id = ?",
				foreign.conversationId(), contactUser)).isZero();
	}

	private static String validMessage(String text) {
		return "{\"bodyText\":\"" + text + "\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}";
	}

	private static String withoutInstance(String problem) {
		return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private long supplier(String code) {
		return jdbc.queryForObject("SELECT id FROM supplier WHERE code = ?", Long.class, code);
	}

	/** A Tunisian plant request taken to sourcing, with an RFQ inviting the given suppliers. */
	private long rfqInviting(ApiClient buyer, long... suppliers) {
		long requestId = accept(assign(approve(submit(signedIn("requester.tn@demo.local"), request("Cross-access RFQ")))));
		StringBuilder ids = new StringBuilder();
		for (long id : suppliers) {
			ids.append(ids.isEmpty() ? "" : ",").append(id);
		}
		return ((Number) buyer.post("/api/v1/sourcing/requests/" + requestId + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + ids + "]}").expectStatus(200).json("$.id")).longValue();
	}

	/** The buyer opens the RFQ conversation with one supplier and writes an external message there. */
	private Thread open(ApiClient buyer, long rfq, long supplier, String text) {
		long conversation = ((Number) buyer.post("/api/v1/messaging/conversations",
				"{\"contextType\":\"RFQ\",\"contextId\":" + rfq + ",\"supplierId\":" + supplier + "}")
			.expectStatus(200)
			.json("$.id")).longValue();
		long message = ((Number) buyer.post("/api/v1/messaging/conversations/" + conversation + "/messages",
				"{\"audience\":\"EXTERNAL\",\"bodyText\":\"" + text + "\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}")
			.expectStatus(201)
			.json("$.id")).longValue();
		return new Thread(conversation, message, text);
	}

	private long accept(long requestId) {
		ApiClient buyer = signedIn("buyer@demo.local");
		int version = ((Number) buyer.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		buyer.post("/api/v1/purchasing/requests/" + requestId + "/accept", "{\"version\":" + version + "}").expectStatus(200);
		return requestId;
	}

	private long assign(long requestId) {
		ApiClient manager = signedIn("purchasing.manager@demo.local");
		int version = ((Number) manager.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		manager.post("/api/v1/purchasing/requests/" + requestId + "/assign",
				"{\"version\":" + version + ",\"buyerUserId\":" + userId("buyer@demo.local") + "}").expectStatus(200);
		return requestId;
	}

	private long approve(long requestId) {
		ApiClient approver = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = approver.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		long task = ((Number) approval.json("$.myTask.id")).longValue();
		int version = ((Number) approval.json("$.myTask.version")).intValue();
		approver.post("/api/v1/approvals/tasks/" + task + "/approve", "{\"version\":" + version + "}").expectStatus(200);
		return requestId;
	}

	private long submit(ApiClient client, Map<String, Object> body) {
		ApiClient.Response draft = client.post("/api/v1/requests", JSON.writeValueAsString(body)).expectStatus(201);
		return client.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();
	}

	private Map<String, Object> request(String title) {
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", jdbc.queryForObject("SELECT c.id FROM request_category c JOIN organization_group g "
				+ "ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'", Long.class));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "LOCAL");
		body.put("currency", "TND");
		body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, tn));
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(21).toString());
		body.put("lines", new ArrayList<>(List.of(Map.of("description", title, "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 20))));
		return body;
	}

}
