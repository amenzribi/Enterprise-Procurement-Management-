package com.epi.procurement.ordering;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

class OrderingIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void awardThenPoDraftIssueConfirm() {
		long id = commerciallyValidated("Award seals");
		ApiClient ines = signedIn("buyer@demo.local");
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id"))
			.longValue();

		signedIn("requester.fr@demo.local").post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":1,\"quotationId\":" + quoteId + ",\"reason\":\"Best landed\"}").expectStatus(404);

		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId
						+ ",\"reason\":\"Best landed cost for seals\"}")
			.expectStatus(200);
		assertThat((String) signedIn("requester.tn@demo.local").get("/api/v1/requests/" + id).json("$.status"))
			.isEqualTo("AWARDED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE request_id = ?", Integer.class, id))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM price_memory WHERE award_id = (SELECT id FROM award WHERE request_id = ?)",
				Integer.class, id)).isGreaterThanOrEqualTo(1);

		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId + ",\"reason\":\"Again\"}")
			.expectStatus(409);

		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response po = ines.post("/api/v1/ordering/requests/" + id + "/purchase-order",
				"{\"requestVersion\":" + version
						+ ",\"paymentTerms\":\"Net 30\",\"incoterm\":\"DAP\",\"deliveryAddress\":\"Plant warehouse\"}")
			.expectStatus(200);
		assertThat((String) po.json("$.status")).isEqualTo("DRAFT");
		assertThat((String) po.json("$.number")).matches("DEMO-[A-Z0-9]+-PO-\\d{4}-\\d{6}");
		long poId = ((Number) po.json("$.id")).longValue();
		int poVersion = ((Number) po.json("$.version")).intValue();

		ines.post("/api/v1/ordering/purchase-orders/" + poId + "/issue", "{\"version\":" + poVersion + "}")
			.expectStatus(200);
		assertThat((String) signedIn("requester.tn@demo.local").get("/api/v1/requests/" + id).json("$.status"))
			.isEqualTo("ORDERED");
		assertThat((String) ines.get("/api/v1/ordering/requests/" + id + "/purchase-order").json("$.status"))
			.isEqualTo("ISSUED");

		poVersion = ((Number) ines.get("/api/v1/ordering/requests/" + id + "/purchase-order").json("$.version"))
			.intValue();
		ines.post("/api/v1/ordering/purchase-orders/" + poId + "/confirm",
				"{\"version\":" + poVersion + ",\"confirmationNote\":\"Supplier OK (demo)\"}").expectStatus(200);
		assertThat((String) ines.get("/api/v1/ordering/requests/" + id + "/purchase-order").json("$.status"))
			.isEqualTo("CONFIRMED");
	}

	@Test
	void cannotAwardBeforeCommercialValidationOrWrongQuote() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("Too early award")))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/quotes", JSON.writeValueAsString(Map.of("rfqSupplierId",
				rfqSupplierId, "currency", "EUR", "freightAmount", 0, "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 25))))).expectStatus(200);
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id"))
			.longValue();
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId + ",\"reason\":\"Too early\"}")
			.expectStatus(409);

		long other = commerciallyValidated("Other request quote");
		long otherQuote = ((Number) ines.get("/api/v1/sourcing/requests/" + other + "/rfq").json("$.quotes[0].id"))
			.longValue();
		long ready = commerciallyValidated("Wrong quote award");
		version = ((Number) ines.get("/api/v1/requests/" + ready).json("$.version")).intValue();
		ines.post("/api/v1/ordering/requests/" + ready + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + otherQuote
						+ ",\"reason\":\"Wrong request quote\"}")
			.expectStatus(422);
	}

	@Test
	void concurrentAwardOnlyOneSucceeds() throws Exception {
		long id = commerciallyValidated("Concurrent award");
		ApiClient ines = signedIn("buyer@demo.local");
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id"))
			.longValue();
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		String body = "{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId
				+ ",\"reason\":\"Concurrent award race\"}";
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> call = () -> signedIn("buyer@demo.local")
				.post("/api/v1/ordering/requests/" + id + "/award", body)
				.status();
			Future<Integer> a = pool.submit(call);
			Future<Integer> b = pool.submit(call);
			List<Integer> statuses = List.of(a.get(), b.get());
			assertThat(statuses).contains(200);
			assertThat(statuses.stream().filter(s -> s != 200).findFirst().orElse(200)).isIn(409, 400);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE request_id = ?", Integer.class, id))
				.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private long commerciallyValidated(String title) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request(title)))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/quotes", JSON.writeValueAsString(Map.of("rfqSupplierId",
				rfqSupplierId, "currency", "EUR", "freightAmount", 10, "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 25))))).expectStatus(200);
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical",
				"{\"requestVersion\":" + version + ",\"comment\":\"Fits\"}").expectStatus(200);
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-commercial",
				"{\"requestVersion\":" + version + ",\"comment\":\"Price OK\"}").expectStatus(200);
		return id;
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
