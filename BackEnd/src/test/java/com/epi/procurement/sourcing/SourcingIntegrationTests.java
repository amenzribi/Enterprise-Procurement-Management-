package com.epi.procurement.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

class SourcingIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void acceptedBuyerOpensRfqEntersQuoteComparesAndValidates() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Sourcing seals")))));

		signedIn("requester.fr@demo.local").get("/api/v1/sourcing/requests/" + id + "/rfq").expectStatus(404);
		signedIn("requester.fr@demo.local").post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[1]}").expectStatus(404);

		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.status")).isEqualTo("SOURCING");

		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/quotes", JSON.writeValueAsString(Map.of("rfqSupplierId",
				rfqSupplierId, "currency", "EUR", "freightAmount", 10, "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 25))))).expectStatus(200);
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.status")).isEqualTo("QUOTES_RECEIVED");

		@SuppressWarnings("unchecked")
		List<Object> rows = ines.get("/api/v1/sourcing/rfqs/" + rfqId + "/comparison").expectStatus(200).json("$.rows");
		assertThat(rows).isNotEmpty();

		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical",
				"{\"requestVersion\":" + version + ",\"comment\":\"Fits the pump\"}").expectStatus(200);
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-commercial",
				"{\"requestVersion\":" + version + ",\"comment\":\"Price OK\"}").expectStatus(200);
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.status")).isEqualTo("COMMERCIAL_VALIDATION");

		ApiClient.Response link = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link",
				"{}").expectStatus(200);
		// RFQ still open until closed; second quote path covered by public API below on a fresh request.
		assertThat((String) link.json("$.path")).startsWith("/q#");
	}

	@Test
	void publicQuoteLinkSubmitsWithoutAccount() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Public link quote")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		String token = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");

		ApiClient anon = client();
		anon.csrf();
		anon.post("/api/v1/quote-links/summary", "{\"token\":" + JSON.writeValueAsString(token) + "}").expectStatus(200);
		anon.post("/api/v1/quote-links/submit", JSON.writeValueAsString(Map.of("token", token, "currency", "EUR",
				"freightAmount", 5, "dutyAmount", 1, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 30))))).expectStatus(200);
		assertThat((String) sami.get("/api/v1/requests/" + id).json("$.status")).isEqualTo("QUOTES_RECEIVED");
		anon.post("/api/v1/quote-links/summary", "{\"token\":\"not-a-real-token\"}").expectStatus(404);
		anon.post("/api/v1/quote-links/summary", "{\"token\":\"short\"}").expectStatus(404);
	}

	@Test
	void quoteChargesCannotBeNegativeAndAreRoundedToMinorUnits() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Quote charges")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		String token = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");
		ApiClient anon = client();
		anon.csrf();
		anon.post("/api/v1/quote-links/submit", JSON.writeValueAsString(Map.of("token", token, "currency", "EUR",
				"freightAmount", -500, "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 30))))).expectStatus(422);
		anon.post("/api/v1/quote-links/submit", JSON.writeValueAsString(Map.of("token", token, "currency", "EUR",
				"freightAmount", new java.math.BigDecimal("5.005"), "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 30))))).expectStatus(200);
		assertThat(jdbc.queryForObject("SELECT freight_amount FROM quotation WHERE rfq_id = ?", java.math.BigDecimal.class,
				rfqId)).isEqualByComparingTo("5.01");
	}

	@Test
	void concurrentFirstSupplierSubmissionsDoNotFail() throws Exception {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Double submit")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		String token = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");
		String body = JSON.writeValueAsString(Map.of("token", token, "currency", "EUR", "freightAmount", 1, "dutyAmount",
				0, "otherAmount", 0, "lines", List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 30))));
		var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
		try {
			java.util.concurrent.Callable<Integer> call = () -> {
				ApiClient anon = client();
				anon.csrf();
				return anon.post("/api/v1/quote-links/submit", body).status();
			};
			for (var result : pool.invokeAll(List.of(call, call, call, call))) {
				assertThat(result.get()).isEqualTo(200);
			}
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quotation WHERE rfq_id = ?", Integer.class, rfqId))
				.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void supplierLinkIsNeutralOnceEvaluationHasStarted() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Frozen quote")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		String token = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");
		String body = JSON.writeValueAsString(Map.of("token", token, "currency", "EUR", "freightAmount", 1, "dutyAmount",
				0, "otherAmount", 0, "lines", List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 30))));
		ApiClient anon = client();
		anon.csrf();
		anon.post("/api/v1/quote-links/submit", body).expectStatus(200);
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical",
				"{\"requestVersion\":" + version + ",\"comment\":\"Fits\"}").expectStatus(200);
		anon.post("/api/v1/quote-links/submit", body).expectStatus(404);
		anon.post("/api/v1/quote-links/summary", "{\"token\":" + JSON.writeValueAsString(token) + "}").expectStatus(404);
	}

	@Test
	void closingAnRfqTwiceAuditsOnce() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Close twice")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		long rfqId = ((Number) ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200).json("$.id")).longValue();
		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/close", "{}").expectStatus(200);
		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/close", "{}").expectStatus(200);
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' AND entity_id = ?
				AND action = 'RFQ_CLOSED'""", Integer.class, Long.toString(id))).isEqualTo(1);
	}

	@Test
	void unknownQueueFilterValuesAreBadRequests() {
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.get("/api/v1/purchasing/queue?status=NOPE").expectStatus(400);
		mehdi.get("/api/v1/purchasing/queue?priority=NOPE").expectStatus(400);
	}

	@Test
	void reissueQuoteLinkRevokesPreviousTokenAndClosedRfqIsNeutral404() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Revoke link")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"STANDARD\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();

		String first = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");
		String second = ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/suppliers/" + rfqSupplierId + "/link", "{}")
			.expectStatus(200)
			.json("$.token");
		assertThat(second).isNotEqualTo(first);

		ApiClient anon = client();
		anon.csrf();
		anon.post("/api/v1/quote-links/summary", "{\"token\":" + JSON.writeValueAsString(first) + "}").expectStatus(404);
		anon.post("/api/v1/quote-links/summary", "{\"token\":" + JSON.writeValueAsString(second) + "}").expectStatus(200);

		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/close", "{}").expectStatus(200);
		anon.post("/api/v1/quote-links/submit", JSON.writeValueAsString(Map.of("token", second, "currency", "EUR",
				"freightAmount", 0, "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 22))))).expectStatus(404);
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
		ApiClient.Response draft = client.post("/api/v1/requests", json(body)).expectStatus(201);
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

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}
