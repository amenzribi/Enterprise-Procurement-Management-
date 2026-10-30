package com.epi.procurement.logistics;

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

class LogisticsIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void confirmedPoShipmentMilestonesAndDocuments() {
		long id = confirmedPo("Ship seals");
		ApiClient ines = signedIn("buyer@demo.local");
		signedIn("requester.fr@demo.local").get("/api/v1/logistics/requests/" + id + "/shipments").expectStatus(404);

		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		LocalDate planned = LocalDate.now(TUNIS).minusDays(1);
		ApiClient.Response shipment = ines.post("/api/v1/logistics/requests/" + id + "/shipments",
				JSON.writeValueAsString(Map.of("requestVersion", version, "kind", "LOCAL", "carrierName", "Demo Freight",
						"milestonePlans", List.of(Map.of("code", "DISPATCHED", "plannedDate", planned.toString())))))
			.expectStatus(200);
		long shipmentId = ((Number) shipment.json("$.id")).longValue();
		assertThat((String) shipment.json("$.number")).matches("DEMO-[A-Z0-9]+-SH-\\d{4}-\\d{6}");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> milestones = shipment.json("$.milestones");
		assertThat(milestones).hasSize(4);
		Map<String, Object> dispatched = milestones.stream()
			.filter(m -> "DISPATCHED".equals(m.get("code")))
			.findFirst()
			.orElseThrow();
		assertThat(dispatched.get("late")).isEqualTo(true);

		long milestoneId = ((Number) dispatched.get("id")).longValue();
		int shVersion = ((Number) shipment.json("$.version")).intValue();
		ines.put("/api/v1/logistics/shipments/" + shipmentId + "/milestones/" + milestoneId,
				"{\"version\":" + shVersion + ",\"actualDate\":\"" + LocalDate.now(TUNIS) + "\"}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("SHIPPED");

		ines.post("/api/v1/logistics/shipments/" + shipmentId + "/documents",
				"{\"kind\":\"DELIVERY_NOTE\",\"referenceNumber\":\"DN-1\",\"note\":\"Gate A\"}").expectStatus(200);

		Map<String, Object> delivered = milestones.stream()
			.filter(m -> "DELIVERED".equals(m.get("code")))
			.findFirst()
			.orElseThrow();
		ApiClient.Response after = ines.get("/api/v1/logistics/shipments/" + shipmentId).expectStatus(200);
		long deliveredId = ((Number) delivered.get("id")).longValue();
		int v2 = ((Number) after.json("$.version")).intValue();
		ines.put("/api/v1/logistics/shipments/" + shipmentId + "/milestones/" + deliveredId,
				"{\"version\":" + v2 + ",\"actualDate\":\"" + LocalDate.now(TUNIS) + "\"}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("DELIVERED");
		assertThat((String) samiUserStatus(id)).isEqualTo("RECEIVED");
		assertThat((String) samiStatus(id)).isNotEqualTo("ACCEPTED");
	}

	@Test
	void concurrentFullShipmentsOnlyOneSucceeds() throws Exception {
		long id = confirmedPo("Concurrent ship");
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		String body = "{\"requestVersion\":" + version + ",\"kind\":\"LOCAL\"}";
		var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			java.util.concurrent.Callable<Integer> call = () -> signedIn("buyer@demo.local")
				.post("/api/v1/logistics/requests/" + id + "/shipments", body)
				.status();
			var a = pool.submit(call);
			var b = pool.submit(call);
			java.util.List<Integer> statuses = java.util.List.of(a.get(), b.get());
			assertThat(statuses).contains(200);
			assertThat(statuses.stream().filter(s -> s != 200).findFirst().orElse(200)).isIn(409, 400);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment WHERE request_id = ?", Integer.class, id))
				.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void cannotShipBeforeConfirmOrOverQuantity() {
		long id = issuedOnly("No confirm yet");
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/logistics/requests/" + id + "/shipments", "{\"requestVersion\":" + version + "}")
			.expectStatus(409);

		long ready = confirmedPo("Overship");
		version = ((Number) ines.get("/api/v1/requests/" + ready).json("$.version")).intValue();
		long poLineId = jdbc.queryForObject(
				"SELECT id FROM purchase_order_line WHERE purchase_order_id = (SELECT id FROM purchase_order WHERE request_id = ?)",
				Long.class, ready);
		ines.post("/api/v1/logistics/requests/" + ready + "/shipments",
				JSON.writeValueAsString(Map.of("requestVersion", version, "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId, "quantity", 2)))))
			.expectStatus(409);
	}

	private String samiStatus(long id) {
		return signedIn("requester.tn@demo.local").get("/api/v1/requests/" + id).json("$.status");
	}

	private String samiUserStatus(long id) {
		return signedIn("requester.tn@demo.local").get("/api/v1/requests/" + id).json("$.userStatus");
	}

	private long confirmedPo(String title) {
		long id = issuedOnly(title);
		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient.Response po = ines.get("/api/v1/ordering/requests/" + id + "/purchase-order").expectStatus(200);
		long poId = ((Number) po.json("$.id")).longValue();
		int poVersion = ((Number) po.json("$.version")).intValue();
		ines.post("/api/v1/ordering/purchase-orders/" + poId + "/confirm",
				"{\"version\":" + poVersion + ",\"confirmationNote\":\"OK\"}").expectStatus(200);
		return id;
	}

	private long issuedOnly(String title) {
		long id = commerciallyValidated(title);
		ApiClient ines = signedIn("buyer@demo.local");
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id"))
			.longValue();
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId + ",\"reason\":\"Ship award\"}")
			.expectStatus(200);
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response po = ines.post("/api/v1/ordering/requests/" + id + "/purchase-order",
				"{\"requestVersion\":" + version + "}").expectStatus(200);
		ines.post("/api/v1/ordering/purchase-orders/" + ((Number) po.json("$.id")).longValue() + "/issue",
				"{\"version\":" + po.json("$.version") + "}").expectStatus(200);
		return id;
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
				rfqSupplierId, "currency", "EUR", "freightAmount", 0, "dutyAmount", 0, "otherAmount", 0, "lines",
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
		mehdi.post("/api/v1/purchasing/requests/" + requestId + "/assign",
				"{\"version\":" + version + ",\"buyerUserId\":" + userId("buyer@demo.local") + "}").expectStatus(200);
		return requestId;
	}

	private long approve(long requestId) {
		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		karim.post("/api/v1/approvals/tasks/" + approval.json("$.myTask.id") + "/approve",
				"{\"version\":" + approval.json("$.myTask.version") + "}").expectStatus(200);
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
