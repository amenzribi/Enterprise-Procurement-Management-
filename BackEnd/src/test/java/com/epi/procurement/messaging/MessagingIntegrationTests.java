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

class MessagingIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void buyerAndSupplierExchangeExternalMessagesWhileInternalStaysHidden() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long requestId = accept(assign(approve(submit(sami, request("Messaging RFQ")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + requestId + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();

		ApiClient.Response opened = ines.post("/api/v1/messaging/conversations",
				"{\"contextType\":\"RFQ\",\"contextId\":" + rfqId + ",\"supplierId\":" + skf + "}")
			.expectStatus(200);
		long conversationId = ((Number) opened.json("$.id")).longValue();

		String clientId = UUID.randomUUID().toString();
		ines.post("/api/v1/messaging/conversations/" + conversationId + "/messages",
				"{\"audience\":\"EXTERNAL\",\"bodyText\":\"Please confirm lead time\",\"clientMessageId\":\"" + clientId
						+ "\"}")
			.expectStatus(201);
		// Idempotent replay
		ines.post("/api/v1/messaging/conversations/" + conversationId + "/messages",
				"{\"audience\":\"EXTERNAL\",\"bodyText\":\"Please confirm lead time\",\"clientMessageId\":\"" + clientId
						+ "\"}")
			.expectStatus(201);
		ines.post("/api/v1/messaging/conversations/" + conversationId + "/messages",
				"{\"audience\":\"INTERNAL\",\"bodyText\":\"Budget note stays internal\",\"clientMessageId\":\""
						+ UUID.randomUUID() + "\"}")
			.expectStatus(201);

		ApiClient contact = signedIn("contact.skf@demo.local");
		ApiClient.Response portalList = contact.get("/api/v1/supplier-portal/conversations").expectStatus(200);
		assertThat(portalList.body()).contains("\"id\":" + conversationId);
		ApiClient.Response portalMessages = contact
			.get("/api/v1/supplier-portal/conversations/" + conversationId + "/messages")
			.expectStatus(200);
		assertThat(portalMessages.body()).contains("Please confirm lead time");
		assertThat(portalMessages.body()).doesNotContain("Budget note stays internal");

		contact.post("/api/v1/supplier-portal/conversations/" + conversationId + "/messages",
				"{\"bodyText\":\"Lead time is 10 days\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}")
			.expectStatus(201);

		ApiClient.Response buyerMessages = ines.get("/api/v1/messaging/conversations/" + conversationId + "/messages")
			.expectStatus(200);
		assertThat(buyerMessages.body()).contains("Lead time is 10 days");
		assertThat(buyerMessages.body()).contains("Budget note stays internal");

		// Competing supplier contact must not see SKF thread (no contact for Sfax on demo — use Sami).
		ApiClient samiAgain = signedIn("requester.tn@demo.local");
		assertThat(samiAgain.get("/api/v1/messaging/conversations/" + conversationId).expectStatus(403).code())
			.isEqualTo("forbidden");
		assertThat(samiAgain.get("/api/v1/supplier-portal/conversations").expectStatus(403).code())
			.isIn("forbidden", "supplier-portal-only");

		// GLOBAL_ADMIN alone cannot open buyer messaging.
		ApiClient ga = signedIn("global.admin@demo.local");
		assertThat(ga.get("/api/v1/messaging/conversations").expectStatus(403).code()).isEqualTo("forbidden");

		// Supplier contact cannot call admin or purchasing.
		assertThat(contact.get("/api/v1/admin/accounts").expectStatus(403).code()).isIn("forbidden",
				"supplier-portal-only");
		assertThat(contact.get("/api/v1/purchasing/queue").expectStatus(403).code()).isIn("forbidden",
				"supplier-portal-only");
	}

	@Test
	void inviteRejectsInternalAccountsAndUnknownSuppliers() {
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		assertThat(ines
			.post("/api/v1/messaging/suppliers/" + skf + "/contacts",
					"{\"email\":\"buyer@demo.local\",\"displayName\":\"Should fail\"}")
			.expectStatus(409)
			.code()).isEqualTo("account-not-eligible");
		assertThat(ines.get("/api/v1/messaging/suppliers/999999001/contacts").expectStatus(404).code())
			.isEqualTo("not-found");
	}

	@Test
	void revokeRemovesSupplierAccess() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long requestId = accept(assign(approve(submit(sami, request("Messaging revoke")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		long rfqId = ((Number) ines.post("/api/v1/sourcing/requests/" + requestId + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200).json("$.id")).longValue();
		long conversationId = ((Number) ines.post("/api/v1/messaging/conversations",
				"{\"contextType\":\"RFQ\",\"contextId\":" + rfqId + ",\"supplierId\":" + skf + "}")
			.expectStatus(200)
			.json("$.id")).longValue();
		ines.post("/api/v1/messaging/conversations/" + conversationId + "/messages",
				"{\"audience\":\"EXTERNAL\",\"bodyText\":\"Hello\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}")
			.expectStatus(201);

		long contactId = jdbc.queryForObject(
				"SELECT id FROM supplier_contact WHERE user_id = (SELECT id FROM user_account WHERE email = ?) AND supplier_id = ?",
				Long.class, "contact.skf@demo.local", skf);
		ines.post("/api/v1/messaging/contacts/" + contactId + "/revoke", "{}").expectStatus(204);

		ApiClient contact = signedIn("contact.skf@demo.local");
		assertThat(contact.get("/api/v1/supplier-portal/conversations/" + conversationId).expectStatus(404).code())
			.isEqualTo("not-found");

		// Restore demo contact for later tests / other classes in the same JVM.
		jdbc.update("""
				UPDATE supplier_contact SET is_active = 1, revoked_at = NULL, revoked_by_user_id = NULL
				WHERE id = ?""", contactId);
		jdbc.update("UPDATE user_account SET credentials_version = credentials_version + 1 WHERE email = ?",
				"contact.skf@demo.local");
	}

	private long accept(long requestId) {
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		ines.post("/api/v1/purchasing/requests/" + requestId + "/accept", "{\"version\":" + version + "}")
			.expectStatus(200);
		return requestId;
	}

	private long assign(long requestId) {
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		int version = ((Number) mehdi.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		long inesId = userId("buyer@demo.local");
		mehdi.post("/api/v1/purchasing/requests/" + requestId + "/assign",
				"{\"version\":" + version + ",\"buyerUserId\":" + inesId + "}").expectStatus(200);
		return requestId;
	}

	private long approve(long requestId) {
		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		long task = ((Number) approval.json("$.myTask.id")).longValue();
		int version = ((Number) approval.json("$.myTask.version")).intValue();
		karim.post("/api/v1/approvals/tasks/" + task + "/approve", "{\"version\":" + version + "}").expectStatus(200);
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
		body.put("categoryId", jdbc.queryForObject(
				"SELECT c.id FROM request_category c JOIN organization_group g ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'",
				Long.class));
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
